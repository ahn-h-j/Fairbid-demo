package com.cos.fairbid.auction.domain;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

import com.cos.fairbid.auction.domain.exception.InvalidAuctionException;
import com.cos.fairbid.auction.domain.policy.AuctionExtensionPolicy;
import com.cos.fairbid.auction.domain.policy.BidIncrementPolicy;
import com.cos.fairbid.bid.domain.exception.AuctionEndedException;
import com.cos.fairbid.bid.domain.exception.BidTooLowException;
import com.cos.fairbid.bid.domain.exception.InvalidBidException;
import com.cos.fairbid.bid.domain.exception.SelfBidNotAllowedException;

/**
 * 경매 도메인 모델
 * 순수 비즈니스 로직만 포함 (JPA 의존성 없음)
 */
@Getter
@Builder
public class Auction {

    private Long id;
    private Long sellerId;
    private String title;
    private String description;
    private Category category;
    private Long startPrice;
    private Long currentPrice;
    private Long instantBuyPrice;
    private Long bidIncrement;
    private LocalDateTime scheduledEndTime;
    private LocalDateTime actualEndTime;
    private Integer extensionCount;
    private Integer totalBidCount;
    private AuctionStatus status;
    private Long winnerId;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 즉시 구매 관련 필드
    private Long instantBuyerId;                    // 즉시 구매 요청자 ID
    private LocalDateTime instantBuyActivatedTime;  // 즉시 구매 활성화 시간

    // 거래 방식 관련 필드
    private Boolean directTradeAvailable;           // 직거래 가능 여부
    private Boolean deliveryAvailable;              // 택배 가능 여부
    private String directTradeLocation;             // 직거래 희망 위치 (직거래 가능 시 필수)

    // 1순위, 2순위 입찰자 정보 (Redis 캐시에서 관리, 낙찰자 결정에 사용)
    private Long topBidderId;       // 현재 최고 입찰자 ID
    private Long topBidAmount;      // 현재 최고 입찰 금액
    private Long secondBidderId;    // 2순위 입찰자 ID
    private Long secondBidAmount;   // 2순위 입찰 금액

    /**
     * 새로운 경매 생성을 위한 정적 팩토리 메서드
     *
     * @param sellerId              판매자 ID
     * @param title                 경매 제목
     * @param description           경매 설명
     * @param category              카테고리
     * @param startPrice            시작가
     * @param instantBuyPrice       즉시구매가 (nullable)
     * @param duration              경매 기간 (24h/48h)
     * @param imageUrls             이미지 URL 목록
     * @param directTradeAvailable  직거래 가능 여부
     * @param deliveryAvailable     택배 가능 여부
     * @param directTradeLocation   직거래 희망 위치 (직거래 가능 시 필수)
     * @return 생성된 Auction 도메인 객체
     */
    public static Auction create(
            Long sellerId,
            String title,
            String description,
            Category category,
            Long startPrice,
            Long instantBuyPrice,
            AuctionDuration duration,
            List<String> imageUrls,
            Boolean directTradeAvailable,
            Boolean deliveryAvailable,
            String directTradeLocation
    ) {
        // 즉시구매가 검증: 시작가보다 높아야 함
        if (instantBuyPrice != null && instantBuyPrice <= startPrice) {
            throw InvalidAuctionException.instantBuyPriceTooLow(startPrice, instantBuyPrice);
        }

        // 거래 방식 검증: 최소 1개 방식 선택 필수
        boolean isDirect = directTradeAvailable != null && directTradeAvailable;
        boolean isDelivery = deliveryAvailable != null && deliveryAvailable;
        if (!isDirect && !isDelivery) {
            throw InvalidAuctionException.noTradeMethodSelected();
        }

        // 직거래 선택 시 위치 필수
        if (isDirect && (directTradeLocation == null || directTradeLocation.isBlank())) {
            throw InvalidAuctionException.directTradeLocationRequired();
        }

        LocalDateTime now = LocalDateTime.now();

        return Auction.builder()
                .sellerId(sellerId)
                .title(title)
                .description(description)
                .category(category)
                .startPrice(startPrice)
                .currentPrice(startPrice)
                .instantBuyPrice(instantBuyPrice)
                .bidIncrement(calculateBidIncrement(startPrice))
                .scheduledEndTime(now.plus(duration.getDuration()))
                .actualEndTime(null)
                .extensionCount(0)
                .totalBidCount(0)
                .status(AuctionStatus.BIDDING)
                .winnerId(null)
                .imageUrls(imageUrls)
                .createdAt(now)
                .updatedAt(now)
                .directTradeAvailable(isDirect)
                .deliveryAvailable(isDelivery)
                .directTradeLocation(isDirect ? directTradeLocation : null)
                .build();
    }

    /**
     * 현재 가격 구간에 따른 입찰 단위 계산
     * BidIncrementPolicy에 위임
     *
     * @param price 현재 가격
     * @return 입찰 단위
     * @see BidIncrementPolicy
     */
    public static Long calculateBidIncrement(Long price) {
        return BidIncrementPolicy.calculateBaseIncrement(price);
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     * Mapper에서 사용
     */
    public static AuctionBuilder reconstitute() {
        return Auction.builder();
    }

    // =====================================================
    // 비즈니스 로직 메서드
    // =====================================================

    /**
     * 즉시 구매 버튼 활성화 여부를 반환한다
     *
     * 활성화 조건:
     * 1. 즉시구매가가 설정되어 있어야 함
     * 2. 현재가가 즉시구매가의 90% 미만이어야 함
     *
     * @return 즉시 구매 가능하면 true, 아니면 false
     */
    public boolean isInstantBuyEnabled() {
        // 즉시구매가가 설정되지 않았으면 비활성화
        if (instantBuyPrice == null) {
            return false;
        }

        // 현재가가 즉시구매가의 90% 이상이면 비활성화
        long threshold = (long) (instantBuyPrice * 0.9);
        return currentPrice < threshold;
    }

    /**
     * 다음 입찰 가능 최소 금액을 반환한다
     * 현재 최고가 + 입찰 단위
     *
     * @return 다음 입찰 가능 최소 금액
     */
    public Long getNextMinBidPrice() {
        return currentPrice + bidIncrement;
    }

    /**
     * 경매 수정 가능 여부를 반환한다
     * 첫 입찰이 발생하기 전에만 수정 가능
     *
     * @return 수정 가능하면 true, 아니면 false
     */
    public boolean isEditable() {
        return totalBidCount == 0;
    }

    // =====================================================
    // 입찰 관련 비즈니스 로직 메서드
    // =====================================================

    /**
     * 경매 종료 여부를 확인한다
     *
     * @return 종료되었으면 true, 아니면 false
     */
    public boolean isEnded() {
        return status == AuctionStatus.ENDED
                || status == AuctionStatus.FAILED
                || status == AuctionStatus.CANCELLED;
    }

    /**
     * 입찰 자격을 검증한다
     * 1. 경매 종료 여부 확인
     * 2. 본인 경매 입찰 불가 확인
     *
     * @param bidderId 입찰자 ID
     * @throws InvalidBidException        입찰자 ID가 null인 경우
     * @throws AuctionEndedException      경매가 종료된 경우
     * @throws SelfBidNotAllowedException 본인 경매에 입찰 시도 시
     */
    public void validateBidEligibility(Long bidderId) {
        // 입찰자 ID null 체크
        if (bidderId == null) {
            throw InvalidBidException.bidderIdRequired();
        }

        // 경매 종료 여부 확인
        if (isEnded()) {
            throw AuctionEndedException.forBid(this.id);
        }

        // 본인 경매 입찰 불가 확인
        if (this.sellerId.equals(bidderId)) {
            throw SelfBidNotAllowedException.create();
        }
    }

    /**
     * 연장 구간(종료 5분 전) 여부를 확인한다
     * AuctionExtensionPolicy에 위임
     *
     * @return 연장 구간이면 true, 아니면 false
     * @see AuctionExtensionPolicy
     */
    public boolean isInExtensionPeriod() {
        return AuctionExtensionPolicy.isInExtensionPeriod(scheduledEndTime, LocalDateTime.now());
    }

    /**
     * Redis 캐시에서 조회한 최신 가격으로 currentPrice를 갱신한다.
     * 경매 목록 조회 시 RDB의 오래된 가격 대신 Redis의 실시간 가격을 표시하기 위해 사용.
     *
     * @param redisCurrentPrice Redis에서 조회한 현재가
     */
    public void updateCurrentPriceFromCache(Long redisCurrentPrice) {
        if (redisCurrentPrice != null) {
            this.currentPrice = redisCurrentPrice;
        }
    }

    /**
     * 경매 시간을 연장한다
     * AuctionExtensionPolicy에 위임
     *
     * @see AuctionExtensionPolicy
     */
    public void extend() {
        LocalDateTime now = LocalDateTime.now();
        this.scheduledEndTime = AuctionExtensionPolicy.calculateExtendedEndTime(now);
        this.extensionCount++;
        this.updatedAt = now;
    }

    /**
     * 연장 횟수에 따른 할증된 입찰 단위를 계산한다
     * BidIncrementPolicy에 위임
     *
     * @return 할증된 입찰 단위
     * @see BidIncrementPolicy
     */
    public Long getAdjustedBidIncrement() {
        return BidIncrementPolicy.calculateAdjustedIncrement(bidIncrement, extensionCount);
    }

    /**
     * 최소 입찰 가능 금액을 반환한다
     * 현재가 + 할증된 입찰단위
     *
     * @return 최소 입찰 가능 금액
     */
    public Long getMinBidAmount() {
        return currentPrice + getAdjustedBidIncrement();
    }

    /**
     * 입찰을 처리한다
     * 1. 입찰 금액 검증
     * 2. 현재가 갱신
     * 3. 총 입찰수 증가
     * 4. 입찰 단위 재계산
     *
     * @param amount 입찰 금액
     * @throws BidTooLowException 입찰 금액이 최소 금액보다 낮은 경우
     */
    public void placeBid(Long amount) {
        Long minBidAmount = getMinBidAmount();

        // 입찰 금액이 최소 금액보다 낮으면 예외
        if (amount < minBidAmount) {
            throw BidTooLowException.belowMinimum(amount, minBidAmount);
        }

        // 현재가 갱신
        this.currentPrice = amount;

        // 총 입찰수 증가
        this.totalBidCount++;

        // 입찰 단위 재계산 (가격 구간이 변경될 수 있으므로)
        this.bidIncrement = calculateBidIncrement(this.currentPrice);

        this.updatedAt = LocalDateTime.now();
    }

    // =====================================================
    // 낙찰 관련 비즈니스 로직 메서드
    // =====================================================

    /**
     * 경매를 종료하고 낙찰자를 지정한다
     *
     * @param winnerId 낙찰자 ID
     * @throws IllegalStateException BIDDING 또는 INSTANT_BUY_PENDING 상태가 아닌 경우
     * @throws IllegalArgumentException winnerId가 null인 경우
     */
    public void close(Long winnerId) {
        if (this.status != AuctionStatus.BIDDING && this.status != AuctionStatus.INSTANT_BUY_PENDING) {
            throw new IllegalStateException("BIDDING 또는 INSTANT_BUY_PENDING 상태에서만 종료 가능합니다. 현재 상태: " + this.status);
        }
        if (winnerId == null) {
            throw new IllegalArgumentException("낙찰자 ID는 null일 수 없습니다. 낙찰자가 없으면 fail()을 호출하세요.");
        }
        this.status = AuctionStatus.ENDED;
        this.winnerId = winnerId;
        this.actualEndTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 경매를 유찰 처리한다
     * 입찰자가 없거나 2순위 승계 조건 미충족 시 사용
     *
     * @throws IllegalStateException BIDDING, INSTANT_BUY_PENDING 또는 ENDED 상태가 아닌 경우
     */
    public void fail() {
        if (this.status != AuctionStatus.BIDDING
                && this.status != AuctionStatus.INSTANT_BUY_PENDING
                && this.status != AuctionStatus.ENDED) {
            throw new IllegalStateException(
                    "BIDDING, INSTANT_BUY_PENDING 또는 ENDED 상태에서만 유찰 처리 가능합니다. 현재 상태: "
                            + this.status);
        }
        this.status = AuctionStatus.FAILED;
        this.actualEndTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 낙찰자를 변경한다 (2순위 승계 시 사용)
     *
     * @param newWinnerId 새로운 낙찰자 ID
     * @throws IllegalArgumentException newWinnerId가 null인 경우
     */
    public void transferWinner(Long newWinnerId) {
        if (newWinnerId == null) {
            throw new IllegalArgumentException("새로운 낙찰자 ID는 null일 수 없습니다.");
        }
        this.winnerId = newWinnerId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 종료 시간을 변경한다 (테스트용)
     *
     * @param newEndTime 새로운 종료 시간
     */
    public void updateScheduledEndTime(LocalDateTime newEndTime) {
        this.scheduledEndTime = newEndTime;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 종료 시간이 도래했는지 확인한다
     *
     * @return 종료 시간이 지났으면 true
     */
    public boolean isTimeToClose() {
        return LocalDateTime.now().isAfter(scheduledEndTime);
    }

    /**
     * 입찰이 있는지 확인한다
     *
     * @return 입찰이 있으면 true
     */
    public boolean hasBids() {
        return totalBidCount > 0;
    }
}
