package com.cos.fairbid.winning.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.winning.application.port.in.ProcessNoShowUseCase;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;

/**
 * 노쇼 처리 서비스
 *
 * 노쇼 처리 흐름:
 * 1. 응답 기한 만료된 Winning 조회
 * 2. 1순위 노쇼 시:
 *    - 경고 부여
 *    - 2순위가 있고 90% 이상이면 자동 승계
 *    - 아니면 유찰
 * 3. 2순위 노쇼 시 (승계 후):
 *    - 노쇼 처리 안함 (비즈니스 규칙)
 *    - 유찰 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoShowProcessingService implements ProcessNoShowUseCase {

    private final WinningRepositoryPort winningRepository;
    private final NoShowProcessingHelper noShowProcessingHelper;

    @Override
    public void processExpiredPayments() {
        // 1. 응답 기한 만료된 Winning 조회
        List<Winning> expiredWinnings = winningRepository.findExpiredPendingResponses();

        if (expiredWinnings.isEmpty()) {
            return;
        }

        log.info("응답 기한 만료 건 {}건 처리 시작", expiredWinnings.size());

        // 2. 각 만료 건을 독립 트랜잭션에서 처리
        for (Winning winning : expiredWinnings) {
            try {
                // Helper의 REQUIRES_NEW 메서드 호출 (별도 트랜잭션)
                noShowProcessingHelper.processExpiredWinning(winning.getId());
            } catch (Exception e) {
                // 개별 처리 실패해도 다른 건은 계속 처리
                log.error("노쇼 처리 실패 - winningId: {}", winning.getId(), e);
            }
        }
    }

    @Override
    public void processNoShow(Long winningId) {
        // 개별 노쇼 처리도 Helper 사용
        noShowProcessingHelper.processExpiredWinning(winningId);
    }
}
