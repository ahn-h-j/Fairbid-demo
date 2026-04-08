package com.cos.fairbid.ai.adapter.in.dto;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.auction.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 경매 어시스턴트 요청 DTO.
 *
 * AI 추천은 경매 등록 폼의 title/category 입력과 독립적으로 동작한다.
 * 사용자는 이미지만 올리고 추천을 받을 수도 있고, 구조화된 힌트(memo) 를 같이 보내 정확도를 높일 수도 있다.
 *
 * 요청 예시:
 * {
 *   "category": "ELECTRONICS",        // optional - 미지정 시 AI 가 이미지/메모로 추론
 *   "memo": "상품 정보: 맥북 프로 14 M3 ...\n구매 시기: 2024년 1월\n상태: 거의 새것",
 *   "imageUrls": ["https://res.cloudinary.com/.../abc.jpg"]
 * }
 */
public record AiAssistRequest(
        Category category,

        @Size(max = 1000, message = "메모는 1000자 이내여야 합니다.")
        String memo,

        @NotEmpty(message = "이미지 URL은 최소 1장 이상 필요합니다.")
        @Size(max = 5, message = "이미지는 최대 5장까지 첨부할 수 있습니다.")
        List<@NotBlank String> imageUrls
) {

    public AiAssistCommand toCommand() {
        return new AiAssistCommand(
                category,
                memo,
                List.copyOf(imageUrls)
        );
    }
}
