#!/bin/bash -l
# =============================================================================
# Step 5-2 시나리오 C: 장애 격리 테스트
#
# REST/WebSocket 서버 분리 후, 한쪽을 kill해도 다른 쪽이
# 정상 동작하는 것을 증명한다.
#
# 사용법:
#   bash k6/run-step5-fault-isolation-test.sh rest_kill   # REST kill → WS 생존
#   bash k6/run-step5-fault-isolation-test.sh ws_kill     # WS kill → REST 생존
#
# 전제:
#   - REST ASG와 WS ASG가 별도로 구성되어 있어야 함
#   - ALB 라우팅: /api/** → REST ASG, /ws/** → WS ASG
# =============================================================================

ALB_URL="http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com"
REGION="ap-northeast-2"
DURATION=180
KILL_DELAY=30

# ── ASG 이름 설정 (분리 후 구성에 맞게 수정) ──
REST_ASG_NAME="${REST_ASG_NAME:-fairbid-rest-asg}"
WS_ASG_NAME="${WS_ASG_NAME:-fairbid-websocket-asg}"

CASE="${1:-rest_kill}"

# ── 입력 검증 ──
if [ "$CASE" != "rest_kill" ] && [ "$CASE" != "ws_kill" ]; then
    echo ""
    echo "  ❌ 사용법: $0 [rest_kill|ws_kill]"
    echo ""
    exit 1
fi

echo ""
echo "================================================================"
echo "  Step 5-2 시나리오 C: 장애 격리 테스트"
echo "================================================================"
echo ""

if [ "$CASE" = "rest_kill" ]; then
    TARGET_ASG=$REST_ASG_NAME
    SURVIVE_ASG=$WS_ASG_NAME
    echo "  시나리오  : REST 서버 kill → WebSocket 생존 확인"
else
    TARGET_ASG=$WS_ASG_NAME
    SURVIVE_ASG=$REST_ASG_NAME
    echo "  시나리오  : WebSocket 서버 kill → REST 생존 확인"
fi

echo "  kill 대상 : $TARGET_ASG"
echo "  생존 확인 : $SURVIVE_ASG"
echo ""


# ── 1. ASG 상태 확인 ──
echo "────────────────────────────────────────"
echo "  [1/6] 현재 ASG 상태 확인"
echo "────────────────────────────────────────"
echo ""

for ASG in $REST_ASG_NAME $WS_ASG_NAME; do
    IN_SERVICE=$(aws autoscaling describe-auto-scaling-groups \
        --auto-scaling-group-names $ASG \
        --region $REGION \
        --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
        --output text 2>/dev/null)
    echo "  $ASG : InService=$IN_SERVICE"
done
echo ""

# kill 대상 인스턴스 ID
TARGET_INSTANCE=$(aws autoscaling describe-auto-scaling-groups \
    --auto-scaling-group-names $TARGET_ASG \
    --region $REGION \
    --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'].InstanceId | [0]" \
    --output text 2>/dev/null)

if [ -z "$TARGET_INSTANCE" ] || [ "$TARGET_INSTANCE" = "None" ]; then
    echo "  ❌ $TARGET_ASG에 InService 인스턴스가 없습니다."
    exit 1
fi

TARGET_IP=$(aws ec2 describe-instances \
    --instance-ids $TARGET_INSTANCE \
    --region $REGION \
    --query "Reservations[0].Instances[0].PrivateIpAddress" \
    --output text 2>/dev/null)

echo "  kill 대상 : $TARGET_INSTANCE ($TARGET_IP)"
echo ""


# ── 2. k6 시작 (백그라운드) ──
echo "────────────────────────────────────────"
echo "  [2/6] k6 장애 격리 테스트 시작"
echo "────────────────────────────────────────"
echo ""

k6 run \
    --env BASE_URL=$ALB_URL \
    --env CASE=$CASE \
    --env DURATION=$DURATION \
    k6/scenarios/ws-fault-isolation-test.js &
K6_PID=$!

echo "  k6 PID : $K6_PID"
echo ""


# ── 3. 기준선 대기 ──
echo "────────────────────────────────────────"
echo "  [3/6] 기준선 수집 대기 (${KILL_DELAY}초)"
echo "────────────────────────────────────────"
echo ""

sleep $KILL_DELAY


# ── 4. 서버 kill ──
echo "────────────────────────────────────────"
echo "  [4/6] 서버 kill"
echo "────────────────────────────────────────"
echo ""

echo "  대상 : $TARGET_INSTANCE ($TARGET_IP)"
echo ""

aws ec2 terminate-instances \
    --instance-ids $TARGET_INSTANCE \
    --region $REGION \
    --output text 2>/dev/null

echo "  ✅ terminate 명령 전송 완료"
echo ""


# ── 5. kill 후 상태 모니터링 ──
echo "────────────────────────────────────────"
echo "  [5/6] kill 후 상태 모니터링 (60초)"
echo "────────────────────────────────────────"
echo ""

echo "  ┌─────────┬─────────────────┬──────────────────┬────────────────┬──────────┐"
echo "  │ 경과    │ 생존 ASG        │ kill ASG         │ WS 커넥션      │ REST     │"
echo "  ├─────────┼─────────────────┼──────────────────┼────────────────┼──────────┤"

for i in $(seq 1 6); do
    sleep 10

    SURVIVE_IN_SERVICE=$(aws autoscaling describe-auto-scaling-groups \
        --auto-scaling-group-names $SURVIVE_ASG \
        --region $REGION \
        --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
        --output text 2>/dev/null)

    TARGET_IN_SERVICE=$(aws autoscaling describe-auto-scaling-groups \
        --auto-scaling-group-names $TARGET_ASG \
        --region $REGION \
        --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
        --output text 2>/dev/null)

    WS_CONN=$(curl -s --max-time 3 "$ALB_URL/actuator/wsconnections" 2>/dev/null)
    WS_COUNT=$(echo "$WS_CONN" | grep -o '"activeConnections":[0-9]*' | cut -d: -f2 2>/dev/null)
    WS_COUNT=${WS_COUNT:-"N/A"}

    REST_STATUS=$(curl -s --max-time 3 -o /dev/null -w "%{http_code}" "$ALB_URL/actuator/health" 2>/dev/null)

    printf "  │ %3s초   │ InService=%-5s │ InService=%-6s │ %-14s │ HTTP %-3s │\n" \
        "$((i * 10))" "$SURVIVE_IN_SERVICE" "$TARGET_IN_SERVICE" "${WS_COUNT}명" "$REST_STATUS"
done

echo "  └─────────┴─────────────────┴──────────────────┴────────────────┴──────────┘"
echo ""


# ── 6. 결과 수집 ──
echo "────────────────────────────────────────"
echo "  [6/6] 결과 수집"
echo "────────────────────────────────────────"
echo ""

echo "  k6 결과 대기..."
wait $K6_PID
echo ""

echo "  최종 ASG 상태:"
for ASG in $REST_ASG_NAME $WS_ASG_NAME; do
    IN_SERVICE=$(aws autoscaling describe-auto-scaling-groups \
        --auto-scaling-group-names $ASG \
        --region $REGION \
        --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
        --output text 2>/dev/null)
    echo "    $ASG : InService=$IN_SERVICE"
done
echo ""

echo "================================================================"
echo "  테스트 완료"
echo "================================================================"
echo ""
echo "  ASG가 kill된 인스턴스를 자동 교체합니다."
echo "  별도 복원 작업은 필요 없습니다."
echo ""
