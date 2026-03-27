#!/bin/bash -l
# =============================================================================
# Step 5-2 시나리오 A: REST 배포 → WebSocket 커넥션 유지 테스트
#
# REST/WS 분리 환경에서 REST ASG만 Instance Refresh하고
# WebSocket 커넥션이 끊기지 않는 것을 증명한다.
#
# 사용법:
#   bash k6/run-step5-deploy-test.sh
#
# 흐름:
#   1. REST ASG 상태 확인
#   2. k6 WebSocket 연결 시작 (백그라운드)
#   3. 30초 후 REST ASG Instance Refresh 트리거
#   4. k6 결과에서 커넥션 끊김 수 확인 (기대: 0건)
# =============================================================================

ALB_URL="http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com"
ASG_NAME="fairbid-rest-asg"
REGION="ap-northeast-2"
DURATION=300  # Instance Refresh 완료까지 5분

echo ""
echo "================================================================"
echo "  Step 5-2 시나리오 A: REST 배포 → WebSocket 커넥션 유지 테스트"
echo "================================================================"
echo ""
echo "  목적: REST ASG만 롤링 배포 시 WebSocket 커넥션이 끊기지 않는 것을 증명"
echo ""


# ── 1. ASG 상태 확인 ──
echo "────────────────────────────────────────"
echo "  [1/6] 현재 ASG 상태 확인"
echo "────────────────────────────────────────"
echo ""

IN_SERVICE=$(aws autoscaling describe-auto-scaling-groups \
    --auto-scaling-group-names $ASG_NAME \
    --region $REGION \
    --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
    --output text 2>/dev/null)

echo "  InService : $IN_SERVICE"
echo ""

if [ "$IN_SERVICE" -lt 1 ] 2>/dev/null; then
    echo "  ❌ 실행 중인 인스턴스가 없습니다. ASG를 확인하세요."
    exit 1
fi


# ── 2. k6 시작 (백그라운드) ──
echo "────────────────────────────────────────"
echo "  [2/6] k6 WebSocket 연결 시작"
echo "────────────────────────────────────────"
echo ""

k6 run \
    --env BASE_URL=$ALB_URL \
    --env DURATION=$DURATION \
    k6/scenarios/ws-deploy-drop-test.js &
K6_PID=$!

echo "  k6 PID : $K6_PID"
echo ""


# ── 3. 구독자 연결 대기 ──
echo "────────────────────────────────────────"
echo "  [3/6] 구독자 연결 대기 (30초)"
echo "────────────────────────────────────────"
echo ""

sleep 30


# ── 4. Instance Refresh 트리거 ──
echo "────────────────────────────────────────"
echo "  [4/6] Instance Refresh 트리거"
echo "────────────────────────────────────────"
echo ""

REFRESH_RESULT=$(aws autoscaling start-instance-refresh \
    --auto-scaling-group-name $ASG_NAME \
    --region $REGION \
    --preferences '{
        "MinHealthyPercentage": 50,
        "InstanceWarmup": 60
    }' \
    --output json 2>&1)

if echo "$REFRESH_RESULT" | grep -q "InstanceRefreshId"; then
    REFRESH_ID=$(echo "$REFRESH_RESULT" | grep -o '"InstanceRefreshId": "[^"]*"' | cut -d'"' -f4)
    echo "  ✅ Instance Refresh 시작됨"
    echo "  RefreshId : $REFRESH_ID"
else
    echo "  ⚠️ 결과: $REFRESH_RESULT"
    echo "  이미 진행 중이거나 에러일 수 있습니다. k6는 계속 실행됩니다."
fi
echo ""


# ── 5. Refresh 진행 상태 모니터링 ──
echo "────────────────────────────────────────"
echo "  [5/6] Instance Refresh 모니터링"
echo "────────────────────────────────────────"
echo ""

while true; do
    REFRESH_STATUS=$(aws autoscaling describe-instance-refreshes \
        --auto-scaling-group-name $ASG_NAME \
        --region $REGION \
        --query "InstanceRefreshes[0].{Status:Status,Progress:PercentageComplete}" \
        --output text 2>/dev/null)

    PROGRESS=$(echo "$REFRESH_STATUS" | awk '{print $1}')
    STATUS=$(echo "$REFRESH_STATUS" | awk '{print $NF}')

    echo "  [$(date '+%H:%M:%S')] 상태: $STATUS | 진행률: $PROGRESS%"

    if ! kill -0 $K6_PID 2>/dev/null; then
        echo ""
        echo "  k6 테스트 종료됨"
        break
    fi

    if [ "$STATUS" = "Successful" ] || [ "$STATUS" = "Cancelled" ] || [ "$STATUS" = "Failed" ]; then
        echo ""
        echo "  Instance Refresh 완료: $STATUS"
        break
    fi

    sleep 15
done
echo ""


# ── 6. 결과 ──
echo "────────────────────────────────────────"
echo "  [6/6] 결과 수집"
echo "────────────────────────────────────────"
echo ""

echo "  k6 결과 대기..."
wait $K6_PID
echo ""

echo "  테스트 후 ASG 상태:"
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME \
    --region $REGION \
    --query "AutoScalingGroups[0].{Desired:DesiredCapacity,InService:length(Instances[?LifecycleState=='InService'])}" \
    --output table 2>/dev/null
echo ""

echo "================================================================"
echo "  테스트 완료"
echo "================================================================"
echo ""
