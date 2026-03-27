/**
 * WebSocket 메시지 동기화 테스트
 *
 * 목적: 오토스케일링 환경에서 Simple Broker 사용 시
 *       서버 간 WebSocket 메시지 동기화가 안 되는 문제를 정량적으로 증명
 *
 * 시나리오:
 *   1. 경매 1개 생성
 *   2. N명의 유저가 WebSocket으로 해당 경매 구독
 *   3. 1명이 REST API로 입찰
 *   4. 구독자 중 몇 명이 BidUpdateMessage를 수신했는지 측정
 *   5. 수신율 = 수신한 유저 / 전체 구독 유저
 *
 * 기대 결과:
 *   - 서버 1대: 수신율 100%
 *   - 서버 2대 이상: 수신율 < 100% (동기화 실패 증명)
 *
 * 실행:
 *   k6 run --env BASE_URL=http://fairbid-alb-xxx.elb.amazonaws.com k6/scenarios/websocket-sync-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Gauge } from 'k6/metrics';
import ws from 'k6/ws';

const BASE_URL = __ENV.BASE_URL || 'http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com';

// SockJS WebSocket URL 변환: http → ws, /ws → /ws/websocket
const WS_URL = BASE_URL.replace('http://', 'ws://').replace('https://', 'wss://') + '/ws/websocket';

// ── 커스텀 메트릭 ──
const wsConnected = new Counter('ws_connected');           // WebSocket 연결 성공 수
const wsMessageReceived = new Counter('ws_message_received'); // BidUpdate 메시지 수신 수
const wsBidSent = new Counter('ws_bid_sent');               // 입찰 요청 수
const wsReceiveRate = new Rate('ws_receive_rate');           // 메시지 수신율
const wsServerCount = new Gauge('ws_server_count');          // 연결된 서버 수 (고유 IP 수)

// ── 테스트 설정 ──
const SUBSCRIBER_COUNT = 20;  // 경매 구독자 수
const BID_COUNT = 1;          // 입찰 1회만 (서버 간 동기화 실패를 증명하려면 1회여야 함)
const WAIT_FOR_MESSAGE_MS = 5000; // 메시지 대기 시간 (ms)

export const options = {
    scenarios: {
        // 구독자들이 먼저 연결한 뒤 입찰이 발생하도록 단계 구성
        websocket_subscribers: {
            executor: 'shared-iterations',
            vus: SUBSCRIBER_COUNT,
            iterations: SUBSCRIBER_COUNT,
            exec: 'subscriber',
            startTime: '0s',
            maxDuration: '3m',
        },
        bidder: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: BID_COUNT,
            exec: 'bidder',
            startTime: '15s', // 구독자 연결 후 15초 뒤 입찰 시작
            maxDuration: '3m',
        },
    },
    thresholds: {
        ws_connected: ['count>0'],
    },
};

// ── 공유 데이터: setup에서 경매 생성 ──
export function setup() {
    console.log(`🎯 Target: ${BASE_URL}`);
    console.log(`📡 WebSocket: ${WS_URL}`);
    console.log(`👥 구독자: ${SUBSCRIBER_COUNT}명`);
    console.log(`🔨 입찰 횟수: ${BID_COUNT}회`);
    console.log('');

    // 테스트용 경매 생성
    const sellerId = 9999;
    const res = http.post(
        `${BASE_URL}/api/v1/auctions`,
        JSON.stringify({
            title: `WS 동기화 테스트 - ${Date.now()}`,
            description: 'WebSocket 메시지 동기화 테스트용 경매',
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
        // 생성 실패 시 기존 BIDDING 경매 사용
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

    // 서버 식별 확인
    const serverRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, {
        headers: { 'X-User-Id': '1' },
    });
    const serverId = serverRes.headers['X-Server-Id'] || 'unknown';
    const serverIp = serverRes.headers['X-Server-Ip'] || 'unknown';
    console.log(`🖥️ 응답 서버: ${serverId} (${serverIp})`);
    console.log('');

    return { auctionId };
}

/**
 * 구독자: WebSocket으로 경매 토픽 구독 → 메시지 수신 대기
 */
export function subscriber(data) {
    const { auctionId } = data;
    const userId = 1000 + __VU; // 구독자별 고유 ID

    // STOMP CONNECT + SUBSCRIBE를 WebSocket으로 수행
    const res = ws.connect(WS_URL, {}, function (socket) {
        let messageReceived = false;
        let connectedServerId = 'unknown';

        socket.on('open', function () {
            // STOMP CONNECT 프레임 전송
            socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0');
        });

        socket.on('message', function (msg) {
            // STOMP CONNECTED 응답 처리
            if (msg.startsWith('CONNECTED')) {
                wsConnected.add(1);

                // 경매 토픽 구독
                const subscribeFrame =
                    `SUBSCRIBE\nid:sub-${userId}\ndestination:/topic/auctions/${auctionId}\n\n\0`;
                socket.send(subscribeFrame);

                // 어떤 서버에 붙었는지 REST로 확인 (WebSocket 연결과 같은 서버일 수도 아닐 수도)
                const checkRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, {
                    headers: { 'X-User-Id': String(userId) },
                });
                connectedServerId = checkRes.headers['X-Server-Ip'] || 'unknown';
                return;
            }

            // STOMP MESSAGE 프레임 처리 (입찰 업데이트)
            if (msg.startsWith('MESSAGE') && msg.includes('currentPrice')) {
                if (!messageReceived) {
                    messageReceived = true;
                    wsMessageReceived.add(1);
                    wsReceiveRate.add(1);
                    console.log(`  ✅ VU${__VU} 수신 (서버: ${connectedServerId})`);
                }
            }
        });

        socket.on('error', function (e) {
            console.error(`  ❌ VU${__VU} WebSocket 에러: ${e.error()}`);
        });

        // 메시지 대기 (입찰이 들어올 때까지)
        // 구독 후 충분히 기다린 뒤 수신 못했으면 실패로 기록
        socket.setTimeout(function () {
            if (!messageReceived) {
                wsReceiveRate.add(0);
                console.log(`  ❌ VU${__VU} 미수신 (서버: ${connectedServerId})`);
            }
            socket.close();
        }, WAIT_FOR_MESSAGE_MS + 30000); // 구독 후 35초간 대기 (입찰 시작까지 15초 + 여유 20초)
    });

    check(res, {
        'WebSocket 연결 성공': (r) => r && r.status === 101,
    });
}

/**
 * 입찰자: REST API로 입찰 → WebSocket 구독자들이 메시지를 받는지 확인
 */
export function bidder(data) {
    const { auctionId } = data;
    const bidderId = 2000 + __ITER;

    const bidRes = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        JSON.stringify({
            amount: 99999999, // ONE_TOUCH → 서버가 최소 입찰가로 자동 조정
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
                wsBidSent.add(1);
                console.log(`🔨 입찰 #${__ITER + 1} 성공 (서버: ${serverId})`);
            } else {
                console.log(`⚠️ 입찰 #${__ITER + 1} 실패: ${body.error?.code || 'unknown'} (서버: ${serverId})`);
            }
        } catch (e) {
            console.log(`⚠️ 입찰 #${__ITER + 1} 응답 파싱 실패`);
        }
    } else {
        console.log(`❌ 입찰 #${__ITER + 1} HTTP 에러: ${bidRes.status} (서버: ${serverId})`);
    }

    // 다음 입찰 간격 (구독자들이 메시지 수신할 시간 확보)
    sleep(3);
}

/**
 * 테스트 요약
 */
export function handleSummary(data) {
    const metrics = data.metrics;

    const connected = metrics.ws_connected?.values?.count || 0;
    const received = metrics.ws_message_received?.values?.count || 0;
    const bidsSent = metrics.ws_bid_sent?.values?.count || 0;
    const receiveRate = metrics.ws_receive_rate?.values?.rate || 0;

    // 수신율 계산: 전체 구독자 중 메시지를 받은 비율
    const expectedMessages = connected; // 모든 구독자가 받아야 100%
    const actualRate = expectedMessages > 0 ? (received / expectedMessages * 100) : 0;

    const summary = `
========================================
🔄 WebSocket 메시지 동기화 테스트 결과
========================================

📡 WebSocket 연결
- 구독자 연결 성공: ${connected}명 / ${SUBSCRIBER_COUNT}명

🔨 입찰
- 성공한 입찰: ${bidsSent}회 / ${BID_COUNT}회

📨 메시지 수신
- 메시지 수신한 구독자: ${received}명 / ${connected}명
- 메시지 수신율: ${actualRate.toFixed(1)}%

${actualRate >= 100
    ? '✅ 결과: 모든 구독자가 메시지를 수신했습니다 (단일 서버 또는 동기화 정상)'
    : `❌ 결과: 메시지 수신율 ${actualRate.toFixed(1)}% — 서버 간 WebSocket 동기화 실패`
}

========================================
`;

    return {
        stdout: summary,
        'k6/results/websocket-sync-result.json': JSON.stringify(data, null, 2),
    };
}
