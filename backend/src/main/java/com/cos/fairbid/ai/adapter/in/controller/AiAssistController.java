package com.cos.fairbid.ai.adapter.in.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.cos.fairbid.ai.adapter.in.dto.AiAssistRequest;
import com.cos.fairbid.ai.adapter.in.dto.AiAssistResponse;
import com.cos.fairbid.ai.application.port.in.AiUsageLimitUseCase;
import com.cos.fairbid.ai.application.port.in.GenerateAuctionAssistUseCase;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.annotation.RequireOnboarding;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;

/**
 * AI 경매 어시스턴트 REST Controller.
 *
 * 판매자가 경매 등록 폼에서 이미지 + 제목 + 카테고리 + 메모를 입력하고
 * "AI 추천 받기"를 누르면 호출되어 시작가 추천(3구간)과 상품 설명을 받는다.
 *
 * 온보딩 완료한 사용자만 호출할 수 있다 (경매 등록과 동일 정책).
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class AiAssistController {

    private final GenerateAuctionAssistUseCase generateAuctionAssistUseCase;
    private final AiUsageLimitUseCase aiUsageLimitUseCase;

    /**
     * AI 시작가 추천 + 설명 생성.
     *
     * 데모 비용 방어: 호출 전 일일 한도를 검사하고, 추천이 정상 출력으로 성공한 직후에만
     * 사용 횟수를 적립한다. 호출이 실패(예외)하면 적립되지 않으므로 재시도할 수 있다.
     */
    @PostMapping("/auction-assist")
    @RequireOnboarding
    public ResponseEntity<ApiResponse<AiAssistResponse>> generateAuctionAssist(
            @Valid @RequestBody AiAssistRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 1. 한도 검사 (초과 시 AiRateLimitExceededException → 429)
        aiUsageLimitUseCase.ensureWithinLimit(userId);

        // 2. AI 추천 호출 (실패하면 아래 적립 라인에 도달하지 않는다)
        AiAssistResult result = generateAuctionAssistUseCase.generate(request.toCommand());

        // 3. 정상 출력 성공 → 사용 횟수 적립
        aiUsageLimitUseCase.recordSuccess(userId);

        return ResponseEntity.ok(ApiResponse.success(AiAssistResponse.from(result)));
    }
}
