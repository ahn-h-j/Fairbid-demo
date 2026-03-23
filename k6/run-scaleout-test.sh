#!/bin/bash
# =============================================================================
# 오토스케일링 입찰 부하 테스트 실행 스크립트
# k6 실행 + ASG 상태 모니터링을 한번에 처리
#
# 사용법: bash k6/run-scaleout-test.sh
# =============================================================================

ALB_URL="http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com"
ASG_NAME="fairbid-app-asg"

# AWS CLI 경로
export PATH="/c/Program Files/Amazon/AWSCLIV2:$PATH"
AWS_CMD="/c/Program Files/Amazon/AWSCLIV2/aws"

echo "========================================"
echo " FairBid 오토스케일링 입찰 부하 테스트"
echo "========================================"
echo ""

# 테스트 전 ASG 상태
echo "[$(date '+%H:%M:%S')] === 테스트 시작 전 ASG 상태 ==="
"$AWS_CMD" autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME \
  --query "AutoScalingGroups[].{Desired:DesiredCapacity,Running:length(Instances[])}" --output table
echo ""

# 백그라운드로 ASG 모니터링 (30초마다)
(
  while true; do
    DESIRED=$("$AWS_CMD" autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME \
      --query "AutoScalingGroups[].DesiredCapacity" --output text 2>/dev/null)
    RUNNING=$("$AWS_CMD" autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME \
      --query "AutoScalingGroups[].length(Instances[])" --output text 2>/dev/null)
    echo "[$(date '+%H:%M:%S')] ASG: desired=$DESIRED, running=$RUNNING"
    sleep 30
  done
) &
MONITOR_PID=$!

# k6 실행 (입찰 부하)
echo "[$(date '+%H:%M:%S')] k6 입찰 부하 테스트 시작..."
echo ""
k6 run --env BASE_URL=$ALB_URL k6/scenarios/scaleout-bid-test.js
echo ""

# 모니터링 중지
kill $MONITOR_PID 2>/dev/null
wait $MONITOR_PID 2>/dev/null

# 테스트 후 ASG 상태
echo "[$(date '+%H:%M:%S')] === 테스트 종료 후 ASG 상태 ==="
"$AWS_CMD" autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME \
  --query "AutoScalingGroups[].{Desired:DesiredCapacity,Running:length(Instances[])}" --output table
echo ""

# 스케일링 활동 이력
echo "=== ASG 스케일링 이력 ==="
"$AWS_CMD" autoscaling describe-scaling-activities --auto-scaling-group-name $ASG_NAME \
  --max-items 10 --query "Activities[].[StartTime,StatusCode,Description]" --output table
echo ""

echo "========================================"
echo " 테스트 완료"
echo " Grafana: http://13.209.57.69:3001"
echo "========================================"
