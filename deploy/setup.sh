#!/usr/bin/env bash
# =====================================================================
# Oracle Cloud (Ubuntu 22.04) VM 1회 셋업 + FairBid 데모 배포 스크립트
# =====================================================================
# VM 안에서 실행:  bash deploy/setup.sh
# (다음 세션에서 Claude 가 SSH 로 직접 실행하거나, 직접 붙여넣어도 됨)
#
# 사전 준비(.env): deploy/docker-compose.prod.yml 이 참조하는 변수들.
#   DOMAIN=<공인IP를 하이픈으로>.sslip.io   예) 140-238-1-2.sslip.io
#   SPRING_DATASOURCE_DATABASE / _USER / _PASSWORD / _ROOT_PASSWORD
#   ANTHROPIC_API_KEY / GEMINI_API_KEY / NAVER_CLIENT_ID / NAVER_CLIENT_SECRET
#   VITE_CLOUDINARY_CLOUD_NAME / VITE_CLOUDINARY_UPLOAD_PRESET
# =====================================================================
set -euo pipefail

echo "==> 1/4 Docker 설치 확인"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$USER" || true
  echo "    Docker 설치 완료 (그룹 반영 위해 재로그인 필요할 수 있음)"
else
  echo "    Docker 이미 설치됨: $(docker --version)"
fi

echo "==> 2/4 방화벽(iptables) 80/443 개방 — Oracle Ubuntu 기본 REJECT 우회"
# Oracle Ubuntu 이미지는 보안리스트와 별개로 인스턴스 내부 iptables 에 REJECT 규칙이 있다.
# 80/443 을 INPUT 체인 상단에 ACCEPT 로 추가한다. (중복 추가 방지 체크)
sudo iptables -C INPUT -p tcp --dport 80 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 1 -p tcp --dport 80 -j ACCEPT
sudo iptables -C INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 1 -p tcp --dport 443 -j ACCEPT
# 규칙 영구 저장 (netfilter-persistent 없으면 설치)
if ! command -v netfilter-persistent >/dev/null 2>&1; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get update -y
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent
fi
sudo netfilter-persistent save || true

echo "==> 3/4 .env 확인"
if [ ! -f .env ]; then
  echo "    [에러] 프로젝트 루트에 .env 가 없습니다. 키/DB/도메인 설정 후 다시 실행하세요." >&2
  exit 1
fi
grep -q '^DOMAIN=' .env || { echo "    [에러] .env 에 DOMAIN 이 없습니다 (예: DOMAIN=140-238-1-2.sslip.io)" >&2; exit 1; }

echo "==> 4/4 컨테이너 빌드 + 기동"
docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --build

echo ""
echo "완료. 잠시 후 https://$(grep '^DOMAIN=' .env | cut -d= -f2) 로 접속하세요."
echo "상태 확인:  docker compose -f deploy/docker-compose.prod.yml ps"
echo "로그 확인:  docker compose -f deploy/docker-compose.prod.yml logs -f backend"
