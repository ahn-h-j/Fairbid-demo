package com.cos.fairbid.ai.adapter.in.controller;

import com.cos.fairbid.ai.adapter.in.dto.AiAssistRequest;
import com.cos.fairbid.ai.adapter.in.dto.AiAssistResponse;
import com.cos.fairbid.ai.application.port.in.GenerateAuctionAssistUseCase;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.common.annotation.RequireOnboarding;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * AI 시작가 추천 + 설명 생성.
     */
    @PostMapping("/auction-assist")
    @RequireOnboarding
    public ResponseEntity<ApiResponse<AiAssistResponse>> generateAuctionAssist(
            @Valid @RequestBody AiAssistRequest request
    ) {
        AiAssistResult result = generateAuctionAssistUseCase.generate(request.toCommand());
        return ResponseEntity.ok(ApiResponse.success(AiAssistResponse.from(result)));
    }
}
