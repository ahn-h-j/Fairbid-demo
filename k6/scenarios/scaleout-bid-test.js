/**
 * 오토스케일링 검증 - 입찰 부하 테스트
 *
 * 시나리오: 여러 경매에 다수 사용자가 동시 입찰하여 CPU 부하 유발 → 스케일 아웃
 * 목적: 실제 입찰 시나리오로 오토스케일링 검증
 *
 * 실행:
 *   k6 run --env BASE_URL=http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com k6/scenarios/scaleout-bid-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com';

// 커스텀 메트릭
const bidSuccess = new Counter('bid_success');
const bidFailed = new Counter('bid_failed');
const bidErrorRate = new Rate('bid_error_rate');
const bidDuration = new Trend('bid_duration', true);

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        // 부하 → 스케일아웃 → 안정화 확인 (20분)
        // 스케일링 파이프라인: 알람 3분 + 부팅 ~2분 = 약 5분 소요
        // 5분 스케일아웃 + 15분 안정화 관찰
        bid_load: {
            executor: 'constant-vus',
            vus: 80,
            duration: '20m',
        },
    },
    thresholds: {
        // 스케일아웃 테스트이므로 느슨하게
        http_req_failed: ['rate<0.5'],
        bid_error_rate: ['rate<0.9'],  // 입찰 경합 실패 허용
    },
};

/**
 * 셋업: 테스트용 경매 여러 개 생성
 * 하나의 경매에 몰리면 락 경합이 병목이 되므로 분산
 */
export function setup() {
    console.log(`🎯 Target: ${BASE_URL}`);
    console.log('🚀 테스트 셋업: 경매 생성 중...');

    const auctionIds = [];
    const AUCTION_COUNT = 5; // 경매 5개에 분산 입찰

    for (let i = 0; i < AUCTION_COUNT; i++) {
        const sellerId = 9900 + i; // 각기 다른 판매자
        const headers = {
            'Content-Type': 'application/json',
            'X-User-Id': String(sellerId),
        };

        const payload = JSON.stringify({
            title: `스케일아웃 테스트 경매 #${i + 1} - ${Date.now()}`,
            description: '오토스케일링 입찰 부하 테스트용',
            category: 'ELECTRONICS',
            startPrice: 10000,
            instantBuyPrice: 10000000,
            duration: 'HOURS_24',
            directTradeAvailable: false,
            deliveryAvailable: true,
        });

        const res = http.post(`${BASE_URL}/api/v1/auctions`, payload, { headers });

        if (res.status === 200 || res.status === 201) {
            try {
                const body = JSON.parse(res.body);
                if (body.success && body.data) {
                    auctionIds.push(body.data.id);
                    console.log(`  ✅ 경매 #${i + 1} 생성: ID=${body.data.id}`);
                }
            } catch (e) {
                console.warn(`  ⚠️ 경매 #${i + 1} 응답 파싱 실패`);
            }
        } else {
            console.warn(`  ⚠️ 경매 #${i + 1} 생성 실패: ${res.status}`);
        }
    }

    // 생성 실패 시 기존 경매에서 가져오기
    if (auctionIds.length === 0) {
        console.log('📌 경매 생성 실패. 기존 BIDDING 경매를 사용합니다.');
        const listRes = http.get(`${BASE_URL}/api/v1/auctions?status=BIDDING&page=0&size=5`);
        if (listRes.status === 200) {
            const listBody = JSON.parse(listRes.body);
            if (listBody.success && listBody.data?.content?.length > 0) {
                listBody.data.content.forEach(a => auctionIds.push(a.id));
            }
        }
    }

    if (auctionIds.length === 0) {
        throw new Error('테스트용 경매를 생성하거나 찾을 수 없습니다.');
    }

    // 입찰 사전 검증 — 시작 전에 입찰이 되는지 확인
    console.log('\n🔍 입찰 사전 검증 중...');
    const testBidRes = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionIds[0]}/bids`,
        JSON.stringify({ amount: 99999999, bidType: 'ONE_TOUCH' }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': '1',
            },
        }
    );

    if (testBidRes.status === 403) {
        throw new Error('입찰 인증 실패 (403). App 서버에 load-test 프로필이 활성화되어 있는지 확인하세요.');
    }

    try {
        const testBody = JSON.parse(testBidRes.body);
        if (testBody.success) {
            console.log('✅ 입찰 사전 검증 통과');
        } else {
            console.log(`⚠️ 입찰 응답: ${testBody.error?.code || 'unknown'} - 부하 테스트는 계속 진행`);
        }
    } catch (e) {
        console.log(`⚠️ 입찰 응답 파싱 실패 (status: ${testBidRes.status}) - 부하 테스트는 계속 진행`);
    }

    console.log(`\n📊 총 ${auctionIds.length}개 경매에 분산 입찰 시작`);
    console.log('⏳ 약 10분간 부하를 줍니다.\n');

    return { auctionIds };
}

/**
 * 메인: 경매 조회 + 입찰
 */
export default function (data) {
    const { auctionIds } = data;
    // 경매를 랜덤으로 선택하여 분산
    const auctionId = auctionIds[Math.floor(Math.random() * auctionIds.length)];
    const userId = Math.floor(Math.random() * 1000) + 1;
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),
    };

    // 1. 경매 상세 조회 (읽기 부하)
    const detailRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, {
        headers,
        tags: { name: 'get_auction' },
    });

    // 2. 경매 목록 조회 (추가 읽기 부하)
    http.get(`${BASE_URL}/api/v1/auctions?page=0&size=10`, {
        headers,
        tags: { name: 'list_auctions' },
    });

    // 3. 입찰 (쓰기 부하 - ONE_TOUCH)
    const bidPayload = JSON.stringify({
        amount: 99999999,  // ONE_TOUCH에서는 서버가 최소 입찰가로 자동 조정
        bidType: 'ONE_TOUCH',
    });

    const startTime = Date.now();
    const bidRes = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        bidPayload,
        {
            headers,
            tags: { name: 'place_bid' },
        }
    );
    const duration = Date.now() - startTime;
    bidDuration.add(duration);

    // 결과 체크
    if (bidRes.status === 200 || bidRes.status === 201) {
        try {
            const body = JSON.parse(bidRes.body);
            if (body.success) {
                bidSuccess.add(1);
                bidErrorRate.add(0);
            } else {
                bidFailed.add(1);
                bidErrorRate.add(1);
            }
        } catch {
            bidFailed.add(1);
            bidErrorRate.add(1);
        }
    } else {
        bidFailed.add(1);
        bidErrorRate.add(1);
    }

    // 사용자 시뮬레이션 대기 (짧게)
    sleep(Math.random() * 0.5 + 0.1); // 0.1~0.6초
}

/**
 * 테스트 요약
 */
export function handleSummary(data) {
    const metrics = data.metrics;
    const summary = `
========================================
🔄 오토스케일링 입찰 부하 테스트 결과
========================================

📈 요청 통계
- 총 HTTP 요청: ${metrics.http_reqs?.values?.count || 0}
- 성공한 입찰: ${metrics.bid_success?.values?.count || 0}
- 실패한 입찰: ${metrics.bid_failed?.values?.count || 0}

⏱️ 입찰 응답 시간
- 평균: ${(metrics.bid_duration?.values?.avg || 0).toFixed(2)}ms
- p95: ${(metrics.bid_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
- p99: ${(metrics.bid_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
- 최대: ${(metrics.bid_duration?.values?.max || 0).toFixed(2)}ms

❌ 에러율
- HTTP 에러율: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%
- 입찰 에러율: ${((metrics.bid_error_rate?.values?.rate || 0) * 100).toFixed(2)}%

========================================
`;

    return {
        stdout: summary,
        'k6/results/scaleout-bid-result.json': JSON.stringify(data, null, 2),
    };
}
