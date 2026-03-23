#!/bin/bash
# =============================================================================
# WebSocket 메시지 동기화 테스트 실행 스크립트
#
# Step 2: 오토스케일링 환경에서 Simple Broker의 메시지 동기화 실패를 증명
#
# 사용법:
#   bash k6/run-websocket-sync-test.sh [single|multi]
#
#   single: 서버 1대에서 테스트 (기준선, 수신율 100% 확인)
#   multi:  서버 2대 이상에서 테스트 (수신율 하락 확인)
#
# 기본: single
# =============================================================================

ALB_URL="http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com"
ASG_NAME="fairbid-app-asg"
MODE="${1:-single}"

# AWS CLI 경로
export PATH="/c/Program Files/Amazon/AWSCLIV2:$PATH"
AWS_CMD="/c/Program Files/Amazon/AWSCLIV2/aws"

echo "========================================"
echo " WebSocket 메시지 동기화 테스트"
echo " 모드: $MODE"
echo "========================================"
echo ""

if [ "$MODE" = "multi" ]; then
    echo "[$(date '+%H:%M:%S')] 멀티 서버 모드: ASG desired=2로 설정"
    "$AWS_CMD" autoscaling set-desired-capacity \
        --auto-scaling-group-name $ASG_NAME \
        --desired-capacity 2 \
        --region ap-northeast-2

    echo "[$(date '+%H:%M:%S')] 2대가 InService 될 때까지 대기..."
    while true; do
        IN_SERVICE=$("$AWS_CMD" autoscaling describe-auto-scaling-groups \
            --auto-scaling-group-names $ASG_NAME \
            --region ap-northeast-2 \
            --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
            --output text 2>/dev/null)

        echo "  InService: $IN_SERVICE / 2"
        if [ "$IN_SERVICE" -ge 2 ] 2>/dev/null; then
            break
        fi
        sleep 15
    done

    # ALB health check 통과 대기 (추가 30초)
    echo "[$(date '+%H:%M:%S')] ALB health check 통과 대기 (30초)..."
    sleep 30
    echo ""
fi

# 현재 ASG 상태 출력
echo "[$(date '+%H:%M:%S')] === ASG 상태 ==="
"$AWS_CMD" autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME \
    --region ap-northeast-2 \
    --query "AutoScalingGroups[0].{Desired:DesiredCapacity,InService:length(Instances[?LifecycleState=='InService'])}" \
    --output table 2>/dev/null
echo ""

# ALB 타겟 health 상태
echo "[$(date '+%H:%M:%S')] === ALB Target Health ==="
TG_ARN=$("$AWS_CMD" elbv2 describe-target-groups --names fairbid-app-tg \
    --region ap-northeast-2 --query "TargetGroups[0].TargetGroupArn" --output text 2>/dev/null)
"$AWS_CMD" elbv2 describe-target-health --target-group-arn "$TG_ARN" \
    --region ap-northeast-2 \
    --query "TargetHealthDescriptions[].{Target:Target.Id,Health:TargetHealth.State}" \
    --output table 2>/dev/null
echo ""

# k6 실행
echo "[$(date '+%H:%M:%S')] k6 WebSocket 동기화 테스트 시작..."
echo ""
k6 run --env BASE_URL=$ALB_URL k6/scenarios/websocket-sync-test.js
echo ""

# 결과 후 ASG 상태
echo "[$(date '+%H:%M:%S')] === 테스트 후 ASG 상태 ==="
"$AWS_CMD" autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME \
    --region ap-northeast-2 \
    --query "AutoScalingGroups[0].{Desired:DesiredCapacity,InService:length(Instances[?LifecycleState=='InService'])}" \
    --output table 2>/dev/null
echo ""

if [ "$MODE" = "multi" ]; then
    echo "[$(date '+%H:%M:%S')] 테스트 완료. desired=1로 리셋할까요? (y/n)"
    read -r answer
    if [ "$answer" = "y" ]; then
        "$AWS_CMD" autoscaling set-desired-capacity \
            --auto-scaling-group-name $ASG_NAME \
            --desired-capacity 1 \
            --region ap-northeast-2
        echo "  ✅ ASG 1대로 리셋 완료"
    fi
fi

echo ""
echo "========================================"
echo " 테스트 완료"
echo "========================================"
