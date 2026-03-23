#!/bin/bash
# =============================================================================
# App 서버 시작 스크립트 (ASG Launch Template용)
# AMI에 Docker + 코드가 포함되어 있으므로 pull → build → run
# =============================================================================

# AWS CLI 설치 (SSM Parameter Store 조회에 필요)
if ! command -v aws &> /dev/null; then
    apt-get update -qq && apt-get install -y -qq awscli > /dev/null 2>&1
fi

# git safe directory 설정
git config --system --add safe.directory /home/ubuntu/Fairbid

cd /home/ubuntu/Fairbid

# 기존 컨테이너 전부 중지 (AMI에 남아있는 것들)
docker compose down 2>/dev/null
docker stop $(docker ps -aq) 2>/dev/null
docker rm $(docker ps -aq) 2>/dev/null

# 최신 코드 pull
git pull origin main

# SSM Parameter Store에서 인프라 서버 IP 조회
INFRA_HOST=$(aws ssm get-parameter --name "/fairbid/infra-host" --region ap-northeast-2 --query "Parameter.Value" --output text 2>/dev/null)

if [ -z "$INFRA_HOST" ]; then
    echo "[ERROR] SSM에서 INFRA_HOST를 가져올 수 없습니다."
    exit 1
fi

# .env에 INFRA_HOST 설정 (기존 값 덮어쓰기 또는 추가)
if grep -q "^INFRA_HOST=" .env; then
    sed -i "s/^INFRA_HOST=.*/INFRA_HOST=$INFRA_HOST/" .env
else
    echo "INFRA_HOST=$INFRA_HOST" >> .env
fi

# load-test 프로필 추가 (k6 부하 테스트용 X-User-Id 인증)
if ! grep -q "load-test" .env; then
    sed -i 's/SPRING_PROFILES_ACTIVE=sentinel/SPRING_PROFILES_ACTIVE=sentinel,load-test/' .env
fi

echo "[INFO] INFRA_HOST=$INFRA_HOST"
echo "[INFO] SPRING_PROFILES=$(grep SPRING_PROFILES .env)"

# App만 실행 (인프라 서버 연결)
docker compose -f infra/scaleout/docker-compose-app.yml --env-file .env up -d --build
