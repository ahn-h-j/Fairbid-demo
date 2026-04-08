package com.cos.fairbid.ai.application.port.in;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.domain.AiAssistResult;

/**
 * AI 경매 어시스턴트 UseCase (Port In).
 * 이미지 + 카테고리 + 제목 + 메모를 받아 시작가 추천과 상품 설명을 생성한다.
 */
public interface GenerateAuctionAssistUseCase {

    AiAssistResult generate(AiAssistCommand command);
}
