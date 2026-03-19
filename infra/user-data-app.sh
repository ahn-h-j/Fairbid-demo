#!/bin/bash
# =============================================================================
# App 서버 시작 스크립트 (ASG Launch Template용)
# AMI에 Docker + 코드가 포함되어 있으므로 pull → build → run
# =============================================================================

# git safe directory 설정 (HOME이 없어도 동작하도록 시스템 레벨 설정)
git config --system --add safe.directory /home/ubuntu/Fairbid

cd /home/ubuntu/Fairbid

# 기존 컨테이너 전부 중지 (AMI에 남아있는 것들)
docker compose down 2>/dev/null
docker stop $(docker ps -aq) 2>/dev/null
docker rm $(docker ps -aq) 2>/dev/null

# 최신 코드 pull
git pull origin main

# INFRA_HOST 설정
grep -q INFRA_HOST .env || echo "INFRA_HOST=${infra_private_ip}" >> .env

# App만 실행 (인프라 서버 연결)
docker compose -f infra/scaleout/docker-compose-app.yml --env-file .env up -d --build
