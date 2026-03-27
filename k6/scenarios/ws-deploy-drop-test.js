/**
 * Step 5-1 시나리오 A: 배포 시 WebSocket 커넥션 드롭 테스트
 *
 * 목적: 모놀리스 환경에서 롤링 배포(Instance Refresh) 시
 *       WebSocket 커넥션이 강제로 끊기는 것을 정량적으로 증명
 *
 * 시나리오:
 *   1. 경매 1개 생성
 *   2. N명의 유저가 WebSocket으로 해당 경매 구독
 *   3. 모든 구독자가 연결된 상태에서 Instance Refresh 트리거 (셸 스크립트에서 수행)
 *   4. 커넥션 끊김 수, 재연결 시도, 메시지 유실 여부 측정
 *
 * 기대 결과:
 *   - 모놀리스: 구 인스턴스 구독자 전원 끊김
 *   - 분리 후: REST만 배포하면 WebSocket 끊김 0건
 *
 * 실행:
 *   k6 run --env BASE_URL=http://ALB_URL k6/scenarios/ws-deploy-drop-test.js
 *
 * 참고:
 *   Instance Refresh는 셸 스크립트(run-step5-deploy-test.sh)에서 트리거한다.
 *   이 스크립트는 WebSocket 연결 유지 + 끊김 감지만 담당한다.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Gauge } from 'k6/metrics';
import ws from 'k6/ws';

const BASE_URL = __ENV.BASE_URL || 'http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com';
const WS_URL = BASE_URL.replace('http://', 'ws://').replace('https://', 'wss://') + '/ws/websocket';

// 테스트 지속 시간: Instance Refresh 완료까지 충분히 길게
// (셸 스크립트에서 Refresh 트리거 후 완료될 때까지 기다림)
const TEST_DURATION_SEC = parseInt(__ENV.DURATION || '300'); // 기본 5분

// ── 커스텀 메트릭 ──
const wsConnected = new Counter('ws_connected');                 // 연결 성공 수
const wsDisconnected = new Counter('ws_disconnected');           // 연결 끊김 수
const wsDisconnectRate = new Rate('ws_disconnect_rate');         // 끊김 비율
const wsMessageReceived = new Counter('ws_message_received');    // 메시지 수신 수
const wsMessageMissed = new Counter('ws_message_missed');        // 메시지 미수신 수
const wsConnectionDuration = new Gauge('ws_connection_duration'); // 연결 유지 시간 (초)

// ── 테스트 설정 ──
const SUBSCRIBER_COUNT = parseInt(__ENV.SUBSCRIBERS || '100');
const BID_INTERVAL_SEC = 15; // 배포 중에도 입찰을 주기적으로 발생시켜 메시지 유실 확인

export const options = {
    scenarios: {
        // WebSocket 구독자: 연결 유지하며 끊김 감지
        websocket_subscribers: {
            executor: 'shared-iterations',
            vus: SUBSCRIBER_COUNT,
            iterations: SUBSCRIBER_COUNT,
            exec: 'subscriber',
            startTime: '0s',
            maxDuration: `${TEST_DURATION_SEC + 60}s`,
        },
        // 주기적 입찰: 배포 중 메시지 전달 여부 확인
        periodic_bidder: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: `${BID_INTERVAL_SEC}s`,
            duration: `${TEST_DURATION_SEC}s`,
            preAllocatedVUs: 1,
            exec: 'bidder',
            startTime: '20s', // 구독자 연결 후 20초 뒤 시작
        },
    },
    thresholds: {
        ws_connected: ['count>0'],
    },
};

// ── setup: 경매 생성 ──
export function setup() {
    console.log(`🎯 Target: ${BASE_URL}`);
    console.log(`📡 WebSocket: ${WS_URL}`);
    console.log(`👥 구독자: ${SUBSCRIBER_COUNT}명`);
    console.log(`⏱️ 테스트 지속 시간: ${TEST_DURATION_SEC}초`);
    console.log(`🔨 입찰 간격: ${BID_INTERVAL_SEC}초`);
    console.log('');
    console.log('⚠️  Instance Refresh는 셸 스크립트에서 트리거합니다.');
    console.log('    k6가 실행된 후 30초 뒤 Refresh를 시작하세요.');
    console.log('');

    // 테스트용 경매 생성
    const sellerId = 9999;
    const res = http.post(
        `${BASE_URL}/api/v1/auctions`,
        JSON.stringify({
            title: `배포 드롭 테스트 - ${Date.now()}`,
            description: 'Step 5-1 시나리오 A: 배포 시 WebSocket 커넥션 드롭 테스트',
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

    console.log('');
    return { auctionId };
}

/**
 * 구독자: WebSocket 연결 유지 + 끊김 감지
 *
 * - STOMP CONNECT → SUBSCRIBE
 * - 연결 유지하며 메시지 수신 카운트
 * - 끊기면 즉시 기록 (재연결 안 함 — 끊김 자체를 증명하는 게 목적)
 */
export function subscriber(data) {
    const { auctionId } = data;
    const userId = 1000 + __VU;
    const connectTime = Date.now();

    const res = ws.connect(WS_URL, {}, function (socket) {
        let connected = false;
        let messagesReceived = 0;
        let gracefulClose = false; // 정상 종료 플래그

        socket.on('open', function () {
            socket.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0');
        });

        socket.on('message', function (msg) {
            if (msg.startsWith('CONNECTED')) {
                connected = true;
                wsConnected.add(1);

                // 경매 토픽 구독
                socket.send(
                    `SUBSCRIBE\nid:sub-${userId}\ndestination:/topic/auctions/${auctionId}\n\n\0`
                );
                return;
            }

            // 입찰 메시지 수신
            if (msg.startsWith('MESSAGE') && msg.includes('currentPrice')) {
                messagesReceived++;
                wsMessageReceived.add(1);
            }
        });

        socket.on('close', function () {
            const durationSec = (Date.now() - connectTime) / 1000;
            wsConnectionDuration.add(durationSec);

            if (connected && !gracefulClose) {
                // 비정상 끊김 (서버 측에서 끊김)
                wsDisconnected.add(1);
                wsDisconnectRate.add(1);
                console.log(`  🔌 VU${__VU} 비정상 끊김 (유지: ${durationSec.toFixed(1)}초, 수신: ${messagesReceived}건)`);
            }
        });

        socket.on('error', function (e) {
            console.error(`  ❌ VU${__VU} WebSocket 에러: ${e.error()}`);
        });

        // STOMP heartbeat 전송 (ALB idle timeout 60초 대응, 30초 간격)
        socket.setInterval(function () {
            socket.send('\n');
        }, 30000);

        // 테스트 지속 시간만큼 연결 유지
        socket.setTimeout(function () {
            // 정상 종료 (타임아웃까지 끊기지 않았으면 성공)
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
 * 입찰자: 주기적으로 입찰 → 배포 중에도 메시지가 전달되는지 확인
 */
export function bidder(data) {
    const { auctionId } = data;
    const bidderId = 3000 + __ITER;

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
        }
    );

    const serverId = bidRes.headers['X-Server-Ip'] || 'unknown';

    if (bidRes.status === 200 || bidRes.status === 201) {
        try {
            const body = JSON.parse(bidRes.body);
            if (body.success) {
                console.log(`  🔨 입찰 #${__ITER + 1} 성공 (서버: ${serverId})`);
            } else {
                console.log(`  ⚠️ 입찰 #${__ITER + 1} 실패: ${body.error?.code || 'unknown'}`);
            }
        } catch (e) {
            console.log(`  ⚠️ 입찰 #${__ITER + 1} 응답 파싱 실패`);
        }
    } else {
        console.log(`  ❌ 입찰 #${__ITER + 1} HTTP ${bidRes.status}`);
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

    const summary = `
========================================
🔌 배포 시 WebSocket 커넥션 드롭 테스트 결과
========================================

📡 WebSocket 연결
- 연결 성공: ${connected}명 / ${SUBSCRIBER_COUNT}명
- 연결 끊김: ${disconnected}명
- 끊김 비율: ${(disconnectRate * 100).toFixed(1)}%

📨 메시지 수신
- 총 수신 메시지: ${messagesReceived}건

${disconnected > 0
    ? `❌ 결과: ${disconnected}명의 WebSocket 커넥션이 배포 중 끊김 — 모놀리스 배포의 문제 확인`
    : '✅ 결과: WebSocket 커넥션 끊김 없음 (분리 배포 성공 또는 배포 미실행)'}

========================================
`;

    return {
        stdout: summary,
        'k6/results/ws-deploy-drop-result.json': JSON.stringify(data, null, 2),
    };
}
