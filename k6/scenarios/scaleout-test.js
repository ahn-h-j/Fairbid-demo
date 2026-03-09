/**
 * 오토스케일링 검증 테스트
 *
 * 목적: ALB를 통해 부하를 줘서 ASG 스케일 아웃이 실제로 발생하는지 확인
 * 전략: CPU를 빠르게 올리기 위해 REST API를 고강도로 호출
 *
 * 실행:
 *   k6 run --env BASE_URL=http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com k6/scenarios/scaleout-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ALB 엔드포인트 (환경변수로 오버라이드 가능)
const BASE_URL = __ENV.BASE_URL || 'http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com';

// 커스텀 메트릭
const reqSuccess = new Counter('req_success');
const reqFailed = new Counter('req_failed');

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        // CPU를 빠르게 올리기 위해 강한 부하를 오래 유지
        // ASG Target Tracking은 보통 3~5분 연속 임계값 초과 시 스케일 아웃
        sustained_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // 워밍업
                { duration: '1m', target: 200 },    // 200 VU까지 증가
                { duration: '5m', target: 300 },    // 300 VU 5분 유지 (스케일 아웃 트리거)
                { duration: '3m', target: 300 },    // 유지하면서 스케일 아웃 확인
                { duration: '1m', target: 0 },      // 정리
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        // 스케일아웃 테스트이므로 임계값은 느슨하게
        http_req_failed: ['rate<0.5'],  // 50%까지 허용 (부하 과다 시 에러 예상)
    },
};

/**
 * 테스트 전 헬스체크로 ALB 연결 확인
 */
export function setup() {
    console.log(`🎯 Target: ${BASE_URL}`);
    console.log('⏳ 약 10분간 부하를 줍니다. ASG 콘솔에서 인스턴스 수 변화를 확인하세요.');
    console.log('');
    console.log('📊 모니터링 방법:');
    console.log('  1. AWS 콘솔 > EC2 > Auto Scaling Groups > fairbid-app-asg');
    console.log('  2. Grafana: http://13.209.57.69:3001');
    console.log('  3. 터미널: watch -n 10 "aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names fairbid-app-asg --query AutoScalingGroups[].{Desired:DesiredCapacity,Instances:Instances[].InstanceId} --output json"');
    console.log('');

    // ALB 헬스체크
    const res = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
    if (res.status !== 200) {
        console.warn(`⚠️ 헬스체크 실패 (status: ${res.status}). ALB/App 상태를 확인하세요.`);
    } else {
        console.log('✅ ALB 헬스체크 통과');
    }

    return {};
}

/**
 * 메인 부하 함수
 * CPU를 올리기 위해 다양한 API를 빠르게 호출
 */
export default function () {
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': String(Math.floor(Math.random() * 1000) + 1),
    };

    // 1. 경매 목록 조회 (DB 쿼리 + 직렬화 = CPU 소모)
    const listRes = http.get(`${BASE_URL}/api/v1/auctions?page=0&size=20`, {
        headers,
        tags: { name: 'list_auctions' },
    });
    check(listRes, { 'list 200': (r) => r.status === 200 });

    if (listRes.status === 200) {
        reqSuccess.add(1);

        // 2. 경매 상세 조회 (추가 CPU 소모)
        try {
            const body = JSON.parse(listRes.body);
            if (body.success && body.data?.content?.length > 0) {
                const auction = body.data.content[0];
                const detailRes = http.get(`${BASE_URL}/api/v1/auctions/${auction.id}`, {
                    headers,
                    tags: { name: 'get_auction_detail' },
                });
                check(detailRes, { 'detail 200': (r) => r.status === 200 });
            }
        } catch (e) {
            // JSON 파싱 실패는 무시
        }
    } else {
        reqFailed.add(1);
    }

    // 3. 헬스체크 (가벼운 요청으로 RPS 높이기)
    http.get(`${BASE_URL}/actuator/health`, {
        tags: { name: 'health' },
    });

    // sleep을 최소화하여 CPU 부하 극대화
    sleep(Math.random() * 0.3); // 0~0.3초
}

/**
 * 테스트 요약
 */
export function handleSummary(data) {
    const metrics = data.metrics;
    const summary = `
========================================
🔄 오토스케일링 검증 테스트 결과
========================================

📈 요청 통계
- 총 HTTP 요청: ${metrics.http_reqs?.values?.count || 0}
- 성공: ${metrics.req_success?.values?.count || 0}
- 실패: ${metrics.req_failed?.values?.count || 0}

⏱️ 응답 시간
- 평균: ${(metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
- p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
- p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
- 최대: ${(metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms

❌ HTTP 에러율: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%

💡 ASG 상태 확인:
   aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names fairbid-app-asg --query "AutoScalingGroups[].{Desired:DesiredCapacity,Running:length(Instances[])}" --output table

========================================
`;

    return {
        stdout: summary,
        'k6/results/scaleout-test-result.json': JSON.stringify(data, null, 2),
    };
}
