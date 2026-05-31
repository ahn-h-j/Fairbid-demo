#!/usr/bin/env bash
# =====================================================================
# GCP Compute Engine (Ubuntu 22.04/24.04) VM 1회 셋업 + FairBid 데모 배포
# =====================================================================
# VM 안에서 실행:  bash deploy/setup-gcp.sh
#
# Oracle 판(setup.sh)과 차이:
#  - Oracle 전용 iptables REJECT 우회 없음 (GCP는 VPC 방화벽으로 80/443 개방).
#    → VM 생성 시 "HTTP 트래픽 허용" + "HTTPS 트래픽 허용" 체크하면 끝.
#  - swap 2GB 추가 (e2-micro 1GB / e2-small 2GB 부팅 피크 OOM 방지).
#
# 사전(.env, 프로젝트 루트):
#   DOMAIN=fairbid.shop
#   SPRING_DATASOURCE_DATABASE / _USER / _PASSWORD / _ROOT_PASSWORD
#   ANTHROPIC_API_KEY / GEMINI_API_KEY / NAVER_CLIENT_ID / NAVER_CLIENT_SECRET
#   VITE_CLOUDINARY_CLOUD_NAME / VITE_CLOUDINARY_UPLOAD_PRESET
# =====================================================================
set -euo pipefail

echo "==> 1/5 swap 2GB 추가 (메모리 안전망)"
if ! sudo swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  echo "    swap 활성화 완료"
else
  echo "    swap 이미 있음"
fi

echo "==> 2/5 Docker 설치 확인"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$USER" || true
  echo "    Docker 설치 완료 (그룹 반영 위해 재로그인/재SSH 필요할 수 있음)"
else
  echo "    Docker 이미 설치됨: $(docker --version)"
fi

echo "==> 3/5 방화벽 안내 (GCP는 VPC 방화벽 사용 — iptables 손 안 댐)"
echo "    VM 생성 시 'HTTP 허용'+'HTTPS 허용' 체크했으면 80/443 이미 열림."
echo "    안 했으면 콘솔에서 인스턴스에 http-server/https-server 네트워크 태그 추가."

echo "==> 4/5 .env 확인"
if [ ! -f .env ]; then
  echo "    [에러] 프로젝트 루트에 .env 가 없습니다. 키/DB/도메인 설정 후 다시 실행하세요." >&2
  exit 1
fi
grep -q '^DOMAIN=' .env || { echo "    [에러] .env 에 DOMAIN 이 없습니다 (예: DOMAIN=fairbid.shop)" >&2; exit 1; }

echo "==> 5/5 컨테이너 빌드 + 기동"
docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --build

echo ""
echo "완료. 잠시 후 https://$(grep '^DOMAIN=' .env | cut -d= -f2) 로 접속하세요."
echo "  (Cloudflare DNS 의 fairbid.shop A 레코드가 이 VM 공인 IP 를 가리켜야 하고,"
echo "   Caddy 가 Let's Encrypt 인증서를 받으려면 해당 레코드는 회색 구름[DNS only] 권장)"
echo "상태: docker compose -f deploy/docker-compose.prod.yml ps"
echo "로그: docker compose -f deploy/docker-compose.prod.yml logs -f backend"
