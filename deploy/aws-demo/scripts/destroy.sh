#!/usr/bin/env bash
# Removes everything provision.sh created: DNS record, Elastic IP, instance (and its disk with all data),
# security group, key pair, instance role and the documents bucket with all documents. Irreversible.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
[[ -f "$HERE/.state" ]] || { echo "No .state file; nothing to destroy." >&2; exit 1; }
# shellcheck disable=SC1091
source "$HERE/.state"
ZONE_NAME="${ZONE_NAME:-ulesolutions.com}"
# strip CR: aws.exe on Windows prints CRLF
awsr() { aws "$@" | tr -d '\r'; }
ec2() { awsr --region "$REGION" ec2 "$@"; }

read -r -p "Delete $DOMAIN, instance $INSTANCE_ID and ALL its data? Type the domain to confirm: " answer
[[ "$answer" == "$DOMAIN" ]] || { echo "Aborted."; exit 1; }

zone_id=$(awsr route53 list-hosted-zones-by-name --dns-name "$ZONE_NAME." \
  --query "HostedZones[?Name=='$ZONE_NAME.' && Config.PrivateZone==\`false\`].Id | [0]" --output text)
aws route53 change-resource-record-sets --hosted-zone-id "${zone_id#/hostedzone/}" --change-batch "{
  \"Changes\": [{\"Action\": \"DELETE\", \"ResourceRecordSet\": {
    \"Name\": \"$DOMAIN.\", \"Type\": \"A\", \"TTL\": 300, \"ResourceRecords\": [{\"Value\": \"$PUBLIC_IP\"}]}}]
}" >/dev/null || echo "    DNS record already gone"

alloc_id=$(ec2 describe-addresses --filters Name=tag:Name,Values="$NAME" --query 'Addresses[0].AllocationId' --output text)
assoc_id=$(ec2 describe-addresses --filters Name=tag:Name,Values="$NAME" --query 'Addresses[0].AssociationId' --output text)
[[ "$assoc_id" != "None" ]] && ec2 disassociate-address --association-id "$assoc_id"
[[ "$alloc_id" != "None" ]] && ec2 release-address --allocation-id "$alloc_id"

ec2 terminate-instances --instance-ids "$INSTANCE_ID" >/dev/null
ec2 wait instance-terminated --instance-ids "$INSTANCE_ID"
ec2 delete-security-group --group-name "$NAME" 2>/dev/null || true
ec2 delete-key-pair --key-name "$NAME"

# instance role and the documents bucket with every stored document
aws iam remove-role-from-instance-profile --instance-profile-name "$NAME" --role-name "$NAME" 2>/dev/null || true
aws iam delete-instance-profile --instance-profile-name "$NAME" 2>/dev/null || true
aws iam delete-role-policy --role-name "$NAME" --policy-name documents 2>/dev/null || true
aws iam delete-role --role-name "$NAME" 2>/dev/null || true
if [[ -n "${DOCUMENTS_BUCKET:-}" ]]; then
  aws s3 rb "s3://$DOCUMENTS_BUCKET" --force >/dev/null 2>&1 && echo "    deleted bucket $DOCUMENTS_BUCKET" || true
fi
rm -f "$HERE/.state"
echo "==> Removed. The local private key $KEY_FILE was kept; delete it yourself if no longer needed."
