/**
 * Step 5-2 시나리오 C: 장애 격리 테스트
 *
 * 목적: REST/WebSocket 서버 분리 후, 한쪽을 kill해도
 *       다른 쪽이 정상 동작하는 것을 증명
 *
 * 시나리오:
 *   Phase 1 (기준선, 0~30초): WebSocket 연결 + REST 요청 동시 진행 → 둘 다 정상 확인
 *   Phase 2 (장애 주입, 30초~): 셸 스크립트에서 한쪽 서버 kill
 *     - Case A: REST kill → WebSocket 커넥션 유지 확인
 *     - Case B: WS kill → REST 정상 응답 확인
 *   Phase 3 (종료): 결과 수집
 *
 * 실행:
 *   k6 run --env BASE_URL=http://ALB_URL --env CASE=rest_kill k6/scenarios/ws-fault-isolation-test.js
 *   k6 run --env BASE_URL=http://ALB_URL --env CASE=ws_kill k6/scenarios/ws-fault-isolation-test.js
 *
 * 참고:
 *   서버 kill은 셸 스크립트(run-step5-fault-isolation-test.sh)에서 수행한다.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import ws from 'k6/ws';

const BASE_URL = __ENV.BASE_URL || 'http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com';
const WS_URL = BASE_URL.replace('http://', 'ws://').replace('https://', 'wss://') + '/ws/websocket';
const CASE = __ENV.CASE || 'rest_kill'; // rest_kill 또는 ws_kill
const TEST_DURATION_SEC = parseInt(__ENV.DURATION || '180');

// ── 커스텀 메트릭 ──
// WebSocket
const wsConnected = new Counter('ws_connected');
const wsDisconnected = new Counter('ws_disconnected');
const wsDisconnectRate = new Rate('ws_disconnect_rate');
const wsMessageReceived = new Counter('ws_message_received');

// REST
const restSuccess = new Counter('rest_success');
const restFailed = new Counter('rest_failed');
const restSuccessRate = new Rate('rest_success_rate');
const restLatency = new Trend('rest_latency', true);

// Phase 구분 (kill 전후 비교)
const restSuccessRateBefore = new Rate('rest_success_rate_before');
const restSuccessRateAfter = new Rate('rest_success_rate_after');

// ── 테스트 설정 ──
const SUBSCRIBER_COUNT = parseInt(__ENV.SUBSCRIBERS || '100');
// kill은 셸 스크립트에서 30초 후 수행
const KILL_TIME_SEC = 30;

export const options = {
    scenarios: {
        // WebSocket 구독자
        websocket_subscribers: {
            executor: 'shared-iterations',
            vus: SUBSCRIBER_COUNT,
            iterations: SUBSCRIBER_COUNT,
            exec: 'subscriber',
            startTime: '0s',
            maxDuration: `${TEST_DURATION_SEC + 60}s`,
        },
        // REST 요청: 지속적으로 보내서 kill 전후 비교
        rest_monitor: {
            executor: 'constant-arrival-rate',
            rate: 2,
            timeUnit: '1s',
            duration: `${TEST_DURATION_SEC}s`,
            preAllocatedVUs: 5,
            exec: 'restMonitor',
            startTime: '5s',
        },
        // 주기적 입찰: WebSocket 메시지 전달 확인용
        periodic_bidder: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: '10s',
            duration: `${TEST_DURATION_SEC}s`,
            preAllocatedVUs: 2,
            exec: 'bidder',
            startTime: '10s',
        },
    },
    thresholds: {
        ws_connected: ['count>0'],
    },
};

export function setup() {
    console.log(`🎯 Target: ${BASE_URL}`);
    console.log(`📡 WebSocket: ${WS_URL}`);
    console.log(`🔪 테스트 케이스: ${CASE}`);
    console.log(`👥 구독자: ${SUBSCRIBER_COUNT}명`);
    console.log(`⏱️ 테스트 지속 시간: ${TEST_DURATION_SEC}초`);
    console.log('');

    if (CASE === 'rest_kill') {
        console.log('📋 시나리오: REST 서버 kill → WebSocket 생존 확인');
        console.log('   기대: WebSocket 커넥션 유지, REST 요청만 실패');
    } else {
        console.log('📋 시나리오: WebSocket 서버 kill → REST 생존 확인');
        console.log('   기대: REST 정상 응답, WebSocket 커넥션 끊김');
    }
    console.log('');
    console.log(`⚠️  ${KILL_TIME_SEC}초 후 셸 스크립트에서 서버를 kill합니다.`);
    console.log('');

    // 테스트용 경매 생성
    const sellerId = 9999;
    const res = http.post(
        `${BASE_URL}/api/v1/auctions`,
        JSON.stringify({
            title: `장애 격리 테스트 (${CASE}) - ${Date.now()}`,
            description: 'Step 5-2 시나리오 C: 장애 격리 테스트',
            category: 'ELECTRONICS',
            startPrice: 10000,
            instantBuyPrice: 10000000,
            duration: 'HOURS_24',
            directTradeAvailable: false,
            deliveryAvailable: true,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': String(sellerId),
            },
        }
    );

    let auctionId;
    if (res.status === 200 || res.status === 201) {
        const body = JSON.parse(res.body);
        if (body.success && body.data) {
            auctionId = body.data.id;
            console.log(`✅ 경매 생성 완료: ID=${auctionId}`);
        }
    }

    if (!auctionId) {
        console.log('⚠️ 경매 생성 실패, 기존 경매 사용');
        const listRes = http.get(`${BASE_URL}/api/v1/auctions?status=BIDDING&page=0&size=1`);
        if (listRes.status === 200) {
            const listBody = JSON.parse(listRes.body);
            if (listBody.success && listBody.data?.content?.length > 0) {
                auctionId = listBody.data.content[0].id;
            }
        }
    }

    if (!auctionId) {
        throw new Error('테스트용 경매를 생성하거나 찾을 수 없습니다.');
    }

    const startTime = Date.now();
    console.log('');
    return { auctionId, startTime };
}

/**
 * WebSocket 구독자: 연결 유지 + 끊김 감지
 */
export function subscriber(data) {
    const { auctionId, startTime } = data;
    const userId = 1000 + __VU;
    const connectTime = Date.now();

    const res = ws.connect(WS_URL, {}, function (socket) {
        let connected = false;
        let messagesReceived = 0;
        let gracefulClose = false;

        socket.on('open', function () {
            socket.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0');
        });

        socket.on('message', function (msg) {
            if (msg.startsWith('CONNECTED')) {
                connected = true;
                wsConnected.add(1);
                socket.send(
                    `SUBSCRIBE\nid:sub-${userId}\ndestination:/topic/auctions/${auctionId}\n\n\0`
                );
                return;
            }

            if (msg.startsWith('MESSAGE') && msg.includes('currentPrice')) {
                messagesReceived++;
                wsMessageReceived.add(1);
            }
        });

        socket.on('close', function () {
            const durationSec = (Date.now() - connectTime) / 1000;
            const elapsedSec = (Date.now() - startTime) / 1000;

            if (connected && !gracefulClose) {
                wsDisconnected.add(1);
                wsDisconnectRate.add(1);
                const phase = elapsedSec < KILL_TIME_SEC ? 'kill 전' : 'kill 후';
                console.log(`  🔌 VU${__VU} 비정상 끊김 [${phase}] (유지: ${durationSec.toFixed(1)}초, 수신: ${messagesReceived}건)`);
            }
        });

        socket.on('error', function (e) {
            console.error(`  ❌ VU${__VU} WebSocket 에러: ${e.error()}`);
        });

        // STOMP heartbeat 전송 (ALB idle timeout 60초 대응, 30초 간격)
        socket.setInterval(function () {
            socket.send('\n');
        }, 30000);

        socket.setTimeout(function () {
            if (connected) {
                gracefulClose = true;
                wsDisconnectRate.add(0);
            }
            socket.close();
        }, TEST_DURATION_SEC * 1000);
    });

    check(res, {
        'WebSocket 연결 성공': (r) => r && r.status === 101,
    });
}

/**
 * REST 모니터: 지속적으로 REST 요청 → kill 전후 성공률 비교
 */
export function restMonitor(data) {
    const { startTime } = data;
    const elapsedSec = (Date.now() - startTime) / 1000;
    const isAfterKill = elapsedSec > KILL_TIME_SEC;

    const res = http.get(`${BASE_URL}/api/v1/auctions?page=0&size=1`, {
        headers: { 'X-User-Id': '1' },
        timeout: '5s',
    });

    const success = res.status === 200;

    if (success) {
        restSuccess.add(1);
        restSuccessRate.add(1);
    } else {
        restFailed.add(1);
        restSuccessRate.add(0);
    }

    if (isAfterKill) {
        restSuccessRateAfter.add(success ? 1 : 0);
    } else {
        restSuccessRateBefore.add(success ? 1 : 0);
    }

    restLatency.add(res.timings.duration);
}

/**
 * 입찰자: 주기적 입찰 → WebSocket 메시지 전달 확인
 */
export function bidder(data) {
    const { auctionId, startTime } = data;
    const bidderId = 3000 + __ITER;
    const elapsedSec = ((Date.now() - startTime) / 1000).toFixed(0);
    const phase = elapsedSec < KILL_TIME_SEC ? 'kill 전' : 'kill 후';

    const bidRes = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        JSON.stringify({
            amount: 99999999,
            bidType: 'ONE_TOUCH',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': String(bidderId),
            },
            timeout: '5s',
        }
    );

    if (bidRes.status === 200 || bidRes.status === 201) {
        try {
            const body = JSON.parse(bidRes.body);
            if (body.success) {
                console.log(`  🔨 [${phase}] 입찰 #${__ITER + 1} 성공 (${elapsedSec}초)`);
            } else {
                console.log(`  ⚠️ [${phase}] 입찰 #${__ITER + 1} 실패: ${body.error?.code || 'unknown'}`);
            }
        } catch (e) {
            // ignore
        }
    } else {
        console.log(`  ❌ [${phase}] 입찰 #${__ITER + 1} HTTP ${bidRes.status} (${elapsedSec}초)`);
    }
}

/**
 * 테스트 요약
 */
export function handleSummary(data) {
    const metrics = data.metrics;

    const connected = metrics.ws_connected?.values?.count || 0;
    const disconnected = metrics.ws_disconnected?.values?.count || 0;
    const disconnectRate = metrics.ws_disconnect_rate?.values?.rate || 0;
    const messagesReceived = metrics.ws_message_received?.values?.count || 0;

    const restTotal = (metrics.rest_success?.values?.count || 0) + (metrics.rest_failed?.values?.count || 0);
    const restSuccessTotal = metrics.rest_success?.values?.count || 0;
    const restFailedTotal = metrics.rest_failed?.values?.count || 0;

    const beforeRate = metrics.rest_success_rate_before?.values?.rate;
    const afterRate = metrics.rest_success_rate_after?.values?.rate;

    const beforePct = beforeRate !== undefined ? (beforeRate * 100).toFixed(1) : 'N/A';
    const afterPct = afterRate !== undefined ? (afterRate * 100).toFixed(1) : 'N/A';

    let conclusion = '';
    if (CASE === 'rest_kill') {
        conclusion = disconnected === 0
            ? '✅ REST kill 후에도 WebSocket 커넥션 전원 생존 — 장애 격리 성공!'
            : `❌ REST kill 후 WebSocket ${disconnected}명 끊김 — 장애 격리 실패`;
    } else {
        conclusion = afterRate !== undefined && afterRate > 0.9
            ? '✅ WebSocket kill 후에도 REST 정상 응답 — 장애 격리 성공!'
            : `❌ WebSocket kill 후 REST 성공률 하락 (${afterPct}%) — 장애 격리 실패`;
    }

    const summary = `
========================================
🔪 장애 격리 테스트 결과 (${CASE})
========================================

📡 WebSocket
- 연결 성공: ${connected}명
- 연결 끊김: ${disconnected}명 (${(disconnectRate * 100).toFixed(1)}%)
- 메시지 수신: ${messagesReceived}건

🌐 REST API
- 총 요청: ${restTotal}건
- 성공: ${restSuccessTotal}건 / 실패: ${restFailedTotal}건
- kill 전 성공률: ${beforePct}%
- kill 후 성공률: ${afterPct}%

${conclusion}

========================================
`;

    return {
        stdout: summary,
        'k6/results/ws-fault-isolation-result.json': JSON.stringify(data, null, 2),
    };
}
