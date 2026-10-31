#!/usr/bin/env bash
# Creates (or reuses) the demo infrastructure. Safe to re-run.
#   - SSH key pair, saved to ~/.ssh/<name>.pem
#   - security group: 80/443 from anywhere, 22 from this machine's public IP only
#   - one free-tier EC2 instance (Ubuntu 24.04, 20 GiB gp3, CPU credits "standard" so it never bills for bursting)
#   - Elastic IP, and a Route 53 A record <domain> -> that IP
#   - private S3 bucket for process documents, and an instance role that may use only that bucket
# Writes the result to ../.state for deploy.sh.
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash on Windows: don't rewrite "/aws/service/..." into a Windows path

HERE="$(cd "$(dirname "$0")/.." && pwd)"
REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-samanvay-demo}"
DOMAIN="${SAMANVAY_DOMAIN:-dev-samanvay.ulesolutions.com}"
ZONE_NAME="${ZONE_NAME:-ulesolutions.com}"
KEY_FILE="${KEY_FILE:-$HOME/.ssh/$NAME.pem}"
VOLUME_GIB="${VOLUME_GIB:-20}"

# strip CR: aws.exe on Windows prints CRLF
awsr() { aws "$@" | tr -d '\r'; }
ec2() { awsr --region "$REGION" ec2 "$@"; }
none() { [[ -z "$1" || "$1" == "None" ]]; }

echo "==> Region $REGION, name $NAME, domain $DOMAIN"

if [[ -z "${INSTANCE_TYPE:-}" ]]; then
  free_types=$(ec2 describe-instance-types --filters Name=free-tier-eligible,Values=true \
    --query 'InstanceTypes[].InstanceType' --output text)
  for t in t3.micro t2.micro; do
    if grep -qw "$t" <<<"$free_types"; then INSTANCE_TYPE=$t; break; fi
  done
  if [[ -z "${INSTANCE_TYPE:-}" ]]; then
    echo "No free-tier x86 micro instance type in $REGION (eligible: $free_types). Set INSTANCE_TYPE." >&2
    exit 1
  fi
fi
echo "    instance type $INSTANCE_TYPE"

# --- key pair
if ! ec2 describe-key-pairs --key-names "$NAME" >/dev/null 2>&1; then
  mkdir -p "$(dirname "$KEY_FILE")"
  ec2 create-key-pair --key-name "$NAME" --key-type ed25519 --query KeyMaterial --output text >"$KEY_FILE"
  chmod 600 "$KEY_FILE"
  echo "    created key pair, private key at $KEY_FILE"
elif [[ ! -f "$KEY_FILE" ]]; then
  echo "Key pair $NAME exists in AWS but $KEY_FILE is missing. Set KEY_FILE or delete the key pair." >&2
  exit 1
fi

# --- security group in the default VPC
vpc_id=$(ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)
if none "$vpc_id"; then
  echo "No default VPC in $REGION. Create one with: aws ec2 create-default-vpc --region $REGION" >&2
  exit 1
fi
sg_id=$(ec2 describe-security-groups --filters Name=group-name,Values="$NAME" Name=vpc-id,Values="$vpc_id" \
  --query 'SecurityGroups[0].GroupId' --output text)
if none "$sg_id"; then
  sg_id=$(ec2 create-security-group --group-name "$NAME" --description "Samanvay demo: web + admin SSH" \
    --vpc-id "$vpc_id" --query GroupId --output text)
  ec2 authorize-security-group-ingress --group-id "$sg_id" --protocol tcp --port 80 --cidr 0.0.0.0/0 >/dev/null
  ec2 authorize-security-group-ingress --group-id "$sg_id" --protocol tcp --port 443 --cidr 0.0.0.0/0 >/dev/null
  ec2 authorize-security-group-ingress --group-id "$sg_id" --protocol udp --port 443 --cidr 0.0.0.0/0 >/dev/null
  echo "    created security group $sg_id"
fi
my_ip=$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')
ec2 authorize-security-group-ingress --group-id "$sg_id" --protocol tcp --port 22 --cidr "$my_ip/32" \
  >/dev/null 2>&1 && echo "    allowed SSH from $my_ip" || true

# --- instance
instance_id=$(ec2 describe-instances \
  --filters Name=tag:Name,Values="$NAME" Name=instance-state-name,Values=pending,running,stopping,stopped \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)
if none "$instance_id"; then
  ami_id=$(awsr --region "$REGION" ssm get-parameters \
    --names /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
    --query 'Parameters[0].Value' --output text)
  root_device=$(ec2 describe-images --image-ids "$ami_id" --query 'Images[0].RootDeviceName' --output text)
  instance_id=$(cd "$HERE" && ec2 run-instances \
    --image-id "$ami_id" \
    --instance-type "$INSTANCE_TYPE" \
    --key-name "$NAME" \
    --security-group-ids "$sg_id" \
    --credit-specification CpuCredits=standard \
    --metadata-options HttpTokens=required,HttpEndpoint=enabled \
    --block-device-mappings "DeviceName=$root_device,Ebs={VolumeSize=$VOLUME_GIB,VolumeType=gp3,Encrypted=true,DeleteOnTermination=true}" \
    --user-data file://scripts/user-data.sh \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$NAME}]" \
                         "ResourceType=volume,Tags=[{Key=Name,Value=$NAME}]" \
    --query 'Instances[0].InstanceId' --output text)
  echo "    launched $instance_id ($ami_id)"
else
  echo "    reusing $instance_id"
  ec2 start-instances --instance-ids "$instance_id" >/dev/null
fi
ec2 wait instance-running --instance-ids "$instance_id"

# --- documents bucket: private, encrypted, reachable only through the instance role
account_id=$(awsr sts get-caller-identity --query Account --output text)
DOCUMENTS_BUCKET="${DOCUMENTS_BUCKET:-$NAME-documents-$account_id}"
if ! awsr s3api head-bucket --bucket "$DOCUMENTS_BUCKET" >/dev/null 2>&1; then
  awsr --region "$REGION" s3api create-bucket --bucket "$DOCUMENTS_BUCKET" \
    --create-bucket-configuration LocationConstraint="$REGION" >/dev/null
  echo "    created bucket $DOCUMENTS_BUCKET"
fi
awsr s3api put-public-access-block --bucket "$DOCUMENTS_BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
awsr s3api put-bucket-encryption --bucket "$DOCUMENTS_BUCKET" --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'
awsr s3api put-bucket-tagging --bucket "$DOCUMENTS_BUCKET" --tagging "TagSet=[{Key=Name,Value=$NAME}]"

# --- instance role: the apps on the box read and write documents in that bucket, nothing else
if ! awsr iam get-role --role-name "$NAME" >/dev/null 2>&1; then
  awsr iam create-role --role-name "$NAME" --description "Samanvay demo instance: documents bucket" \
    --tags Key=Name,Value="$NAME" --assume-role-policy-document \
    '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}' \
    >/dev/null
  echo "    created role $NAME"
fi
awsr iam put-role-policy --role-name "$NAME" --policy-name documents --policy-document "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [
    {\"Effect\": \"Allow\", \"Action\": \"s3:ListBucket\", \"Resource\": \"arn:aws:s3:::$DOCUMENTS_BUCKET\"},
    {\"Effect\": \"Allow\", \"Action\": [\"s3:GetObject\", \"s3:PutObject\", \"s3:DeleteObject\"],
     \"Resource\": \"arn:aws:s3:::$DOCUMENTS_BUCKET/*\"}
  ]
}"
if ! awsr iam get-instance-profile --instance-profile-name "$NAME" >/dev/null 2>&1; then
  awsr iam create-instance-profile --instance-profile-name "$NAME" >/dev/null
  awsr iam add-role-to-instance-profile --instance-profile-name "$NAME" --role-name "$NAME"
  sleep 10 # a new instance profile takes a moment before EC2 accepts it
fi
current_profile=$(ec2 describe-instances --instance-ids "$instance_id" \
  --query 'Reservations[0].Instances[0].IamInstanceProfile.Arn' --output text)
if none "$current_profile"; then
  for i in 1 2 3 4 5 6; do
    ec2 associate-iam-instance-profile --instance-id "$instance_id" --iam-instance-profile Name="$NAME" \
      >/dev/null 2>&1 && break
    [[ $i == 6 ]] && { echo "Could not attach instance profile $NAME." >&2; exit 1; }
    sleep 10
  done
  echo "    attached role $NAME to $instance_id"
fi
# containers are one network hop further than the host; they need hop limit 2 to reach the role's credentials
ec2 modify-instance-metadata-options --instance-id "$instance_id" \
  --http-tokens required --http-endpoint enabled --http-put-response-hop-limit 2 >/dev/null

# --- Elastic IP, so the DNS record survives stop/start
alloc_id=$(ec2 describe-addresses --filters Name=tag:Name,Values="$NAME" --query 'Addresses[0].AllocationId' --output text)
if none "$alloc_id"; then
  alloc_id=$(ec2 allocate-address --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=$NAME}]" --query AllocationId --output text)
fi
ec2 associate-address --allocation-id "$alloc_id" --instance-id "$instance_id" >/dev/null
public_ip=$(ec2 describe-addresses --allocation-ids "$alloc_id" --query 'Addresses[0].PublicIp' --output text)
echo "    Elastic IP $public_ip"

# --- DNS
zone_id=$(awsr route53 list-hosted-zones-by-name --dns-name "$ZONE_NAME." \
  --query "HostedZones[?Name=='$ZONE_NAME.' && Config.PrivateZone==\`false\`].Id | [0]" --output text)
if none "$zone_id"; then
  echo "No public Route 53 hosted zone for $ZONE_NAME in this account." >&2
  exit 1
fi
aws route53 change-resource-record-sets --hosted-zone-id "${zone_id#/hostedzone/}" --change-batch "{
  \"Comment\": \"Samanvay demo\",
  \"Changes\": [{\"Action\": \"UPSERT\", \"ResourceRecordSet\": {
    \"Name\": \"$DOMAIN.\", \"Type\": \"A\", \"TTL\": 300, \"ResourceRecords\": [{\"Value\": \"$public_ip\"}]}}]
}" >/dev/null
echo "    $DOMAIN -> $public_ip"

cat >"$HERE/.state" <<EOF
REGION=$REGION
NAME=$NAME
DOMAIN=$DOMAIN
INSTANCE_ID=$instance_id
PUBLIC_IP=$public_ip
KEY_FILE=$KEY_FILE
DOCUMENTS_BUCKET=$DOCUMENTS_BUCKET
EOF
echo "==> Done. Next: scripts/deploy.sh"
