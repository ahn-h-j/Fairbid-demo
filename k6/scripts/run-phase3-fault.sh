#!/bin/bash
# Phase 3 DB 장애 주입 테스트
#
# Redis Stream MQ에서 DB 장애 시 장애 격리를 증명한다.
#
# 타임라인:
#   0초  : k6 시작 (백그라운드)
#   60초 : docker stop mysql (DB 장애)
#   80초 : docker start mysql (DB 복구)
#   90초 : k6 종료
#   +폴링: 1초 간격으로 정합성 수렴 대기 → 수렴 시간 측정
#
# 사용법: bash k6/scripts/run-phase3-fault.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
GRAFANA_AUTH="admin:admin"
PAUSE_DURATION="${1:-20}"
BASELINE_DURATION="${2:-60}"

# MySQL 접속 정보
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-fairbid}"

# MySQL 컨테이너 이름 자동 감지
MYSQL_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i mysql | head -1)
if [ -z "$MYSQL_CONTAINER" ]; then
    echo "  MySQL 컨테이너를 찾을 수 없습니다."
    exit 1
fi
echo "  MySQL 컨테이너: ${MYSQL_CONTAINER}"

# Grafana Annotation
annotate() {
    local text="$1"
    local tag="$2"
    curl -s -X POST "${GRAFANA_URL}/api/annotations" \
      -u "${GRAFANA_AUTH}" \
      -H "Content-Type: application/json" \
      -d "{\"text\":\"${text}\",\"tags\":[\"${tag}\"]}" > /dev/null 2>&1
    echo "  Annotation: ${text}"
}

echo ""
echo "============================================="
echo "  Phase 3: Redis Stream MQ DB 장애 주입"
echo "============================================="
echo ""
echo "  타임라인:"
echo "  0~${BASELINE_DURATION}초  : Baseline (정상 부하)"
echo "  ${BASELINE_DURATION}~$((BASELINE_DURATION + PAUSE_DURATION))초 : DB 장애 (docker stop)"
echo "  $((BASELINE_DURATION + PAUSE_DURATION))~120초 : 복구 후 안정화"
echo ""

# 1. k6 백그라운드 실행
echo "  k6 부하 테스트 시작 (백그라운드)..."
k6 run "${PROJECT_DIR}/k6/scenarios/bid-sync-test.js" &
K6_PID=$!
echo "  k6 PID: ${K6_PID}"

# 2. Baseline 대기
echo ""
echo "  Baseline 측정 중... (${BASELINE_DURATION}초 대기)"
sleep "${BASELINE_DURATION}"

# 3. 장애 주입
echo ""
echo "  DB 장애 주입!"
annotate "DB 장애 주입 (docker stop ${MYSQL_CONTAINER})" "fault-injection"
docker stop "${MYSQL_CONTAINER}"
echo "  ${MYSQL_CONTAINER} stopped"

# 4. 장애 유지
echo "  장애 유지 중... (${PAUSE_DURATION}초)"
sleep "${PAUSE_DURATION}"

# 5. 복구
echo ""
echo "  DB 복구!"
docker start "${MYSQL_CONTAINER}"
echo "  ${MYSQL_CONTAINER} started"
echo "  DB 기동 대기 중... (10초)"
sleep 10
annotate "DB 복구 (docker start ${MYSQL_CONTAINER})" "recovery"

# 6. k6 종료 대기
echo ""
echo "  k6 종료 대기 중..."
wait "${K6_PID}" || true

# 7. k6 종료 시점 기록
echo ""
echo "  k6 종료 시점: $(date '+%H:%M:%S')"

# 8. 1초 폴링으로 정합성 수렴 대기
echo ""
echo "============================================="
echo "  수렴 대기 (1초 간격 폴링)"
echo "============================================="

START_EPOCH=$(date +%s)

while true; do
    NOW_EPOCH=$(date +%s)
    ELAPSED=$((NOW_EPOCH - START_EPOCH))

    # Redis 입찰 수 집계
    REDIS_COUNT=0
    REDIS_KEYS=$(docker exec fairbid-redis-1 redis-cli KEYS "auction:*" 2>/dev/null)
    while IFS= read -r key; do
        if [[ -z "$key" || "$key" == "auction:closing" ]]; then
            continue
        fi
        count=$(docker exec fairbid-redis-1 redis-cli HGET "$key" totalBidCount 2>/dev/null)
        if [[ -n "$count" && "$count" != "(nil)" ]]; then
            REDIS_COUNT=$((REDIS_COUNT + count))
        fi
    done <<< "$REDIS_KEYS"

    # RDB 입찰 수
    RDB_COUNT=$(docker exec ${MYSQL_CONTAINER} mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D"${MYSQL_DB}" -se "SELECT COUNT(*) FROM bid;" 2>/dev/null || echo "0")

    DIFF=$((REDIS_COUNT - RDB_COUNT))

    printf "\r  [%3ds] Redis=%d  RDB=%d  차이=%d    " "$ELAPSED" "$REDIS_COUNT" "$RDB_COUNT" "$DIFF"

    if [ "$DIFF" -eq 0 ] && [ "$REDIS_COUNT" -gt 0 ]; then
        echo ""
        echo ""
        echo "  정합성 수렴 시점: $(date '+%H:%M:%S')"
        break
    fi

    sleep 1
done

# 9. 결과
CONVERGENCE_SEC=$ELAPSED
echo ""
echo "============================================="
echo "  결과"
echo "============================================="
echo ""
echo "  k6 종료 -> 정합성 수렴: ${CONVERGENCE_SEC}초"
echo "  Redis 입찰 수: ${REDIS_COUNT}"
echo "  RDB 입찰 수:  ${RDB_COUNT}"
echo ""

# 10. Stream 상태
STREAM_LEN=$(docker exec fairbid-redis-1 redis-cli XLEN stream:bid-rdb-sync 2>/dev/null || echo "0")
PENDING_INFO=$(docker exec fairbid-redis-1 redis-cli XPENDING stream:bid-rdb-sync bid-rdb-sync-group 2>/dev/null)
PENDING_COUNT=$(echo "$PENDING_INFO" | head -1)

echo "  Stream 총 메시지: ${STREAM_LEN}"
echo "  PENDING (미처리): ${PENDING_COUNT}"
echo ""
echo "  Grafana 확인: ${GRAFANA_URL}"
echo "============================================="
