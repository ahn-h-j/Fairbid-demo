package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.ai.adapter.in.dto.AiAssistRequest;
import com.cos.fairbid.auction.domain.Category;
import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import com.cos.fairbid.cucumber.config.FakeAiClient;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import io.cucumber.java.ko.조건;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 경매 어시스턴트 인수 테스트 Steps.
 *
 * 외부 Claude API 호출은 FakeAiClient 가 대체한다 (CLAUDE.md §4 — 외부 API mock 허용).
 * 시나리오에서 FakeAiClient 의 모드를 미리 설정한 뒤 컨트롤러를 호출해
 * 정상/실패 응답이 의도대로 반환되는지 검증한다.
 */
public class AiAssistSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;
    private final FakeAiClient fakeAiClient;

    public AiAssistSteps(TestAdapter testAdapter, TestContext testContext, FakeAiClient fakeAiClient) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
        this.fakeAiClient = fakeAiClient;
    }

    /**
     * 시나리오 간 fake 모드가 누수되지 않도록 매 시나리오 시작 전 초기화한다.
     */
    @Before
    public void resetFakeAiClient() {
        fakeAiClient.reset();
    }

    @조건("AI 서비스가 정상 응답하도록 설정되어 있다")
    public void AI_서비스가_정상_응답하도록_설정되어_있다() {
        // Given: FakeAiClient 가 SUCCESS 모드 (default 지만 명시)
        fakeAiClient.setMode(FakeAiClient.Mode.SUCCESS);
    }

    @조건("AI 서비스가 일시적으로 사용할 수 없는 상태이다")
    public void AI_서비스가_일시적으로_사용할_수_없는_상태이다() {
        // Given: FakeAiClient 가 503 으로 응답하도록 설정
        fakeAiClient.setMode(FakeAiClient.Mode.SERVICE_UNAVAILABLE);
    }

    @만약("판매자가 이미지 {int}장과 카테고리 {string}로 AI 추천을 요청한다")
    public void 판매자가_이미지와_카테고리로_AI_추천을_요청한다(int imageCount, String categoryCode) {
        // When: 정상 페이로드로 AI 추천 호출
        AiAssistRequest request = new AiAssistRequest(
                Category.valueOf(categoryCode),
                "상품 정보: 테스트 상품\n상태: 양호",
                generateImageUrls(imageCount)
        );
        ResponseEntity<Map> response = testAdapter.post("/api/v1/ai/auction-assist", request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("판매자가 이미지 없이 AI 추천을 요청한다")
    public void 판매자가_이미지_없이_AI_추천을_요청한다() {
        // When: 이미지 URL 이 빈 리스트인 잘못된 요청 호출 (Bean Validation 으로 400 예상)
        AiAssistRequest request = new AiAssistRequest(
                Category.ELECTRONICS,
                null,
                List.of()
        );
        ResponseEntity<Map> response = testAdapter.post("/api/v1/ai/auction-assist", request, Map.class);
        testContext.setLastResponse(response);
    }

    @그리고("응답 본문의 추천 시작가는 low={long}, mid={long}, high={long} 이다")
    @SuppressWarnings("unchecked")
    public void 응답_본문의_추천_시작가는(long expectedLow, long expectedMid, long expectedHigh) {
        // Then: data.suggestedPrices 의 low/mid/high 검증 (CommonSteps 의 findValue 가 nested 를 못 찾아 별도 step 으로 분리)
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).as("응답 body.data 가 존재해야 합니다").isNotNull();

        Map<String, Object> prices = (Map<String, Object>) data.get("suggestedPrices");
        assertThat(prices).as("응답 body.data.suggestedPrices 가 존재해야 합니다").isNotNull();

        assertThat(((Number) prices.get("low")).longValue()).isEqualTo(expectedLow);
        assertThat(((Number) prices.get("mid")).longValue()).isEqualTo(expectedMid);
        assertThat(((Number) prices.get("high")).longValue()).isEqualTo(expectedHigh);
    }

    @그리고("응답 본문에 생성된 상품 설명이 포함되어 있다")
    @SuppressWarnings("unchecked")
    public void 응답_본문에_생성된_상품_설명이_포함되어_있다() {
        // Then: data.generatedDescription 이 비어있지 않은 문자열인지 검증
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();

        Object description = data.get("generatedDescription");
        assertThat(description).isInstanceOf(String.class);
        assertThat((String) description).isNotBlank();
    }

    private List<String> generateImageUrls(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "https://cdn.example.com/test-image-" + i + ".jpg")
                .toList();
    }
}
