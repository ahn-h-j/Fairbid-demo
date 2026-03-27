package com.cos.fairbid.winning.adapter.in.scheduler;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.winning.application.port.in.CloseAuctionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경매 종료 스케줄러
 * 매초 실행되어 종료 시간이 도래한 경매를 처리한다
 *
 * server.role=api 또는 all에서만 활성화.
 * WS 서버에서 경매 종료 처리가 중복 실행되면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class AuctionClosingScheduler {

    private final CloseAuctionUseCase closeAuctionUseCase;

    /**
     * 매초 실행되어 종료 대상 경매를 처리한다
     * fixedDelay = 1000ms (1초) - 이전 작업 완료 후 1초 대기
     */
    @Scheduled(fixedDelay = 1000)
    public void pollClosingAuctions() {
        try {
            closeAuctionUseCase.closeExpiredAuctions();
        } catch (Exception e) {
            log.error("경매 종료 스케줄러 실행 중 오류 발생", e);
        }
    }
}
