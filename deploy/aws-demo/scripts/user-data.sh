#!/bin/bash
# EC2 first-boot setup (cloud-init): swap for the 1 GiB instance, Docker with Compose.
set -euxo pipefail

if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
sysctl --system

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io docker-compose-v2
apt-get install -y docker-buildx || true
systemctl enable --now docker
usermod -aG docker ubuntu
