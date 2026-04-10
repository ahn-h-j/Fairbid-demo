package com.cos.fairbid.user.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * User 도메인 모델
 * Identity Context의 핵심 도메인 객체로, 사용자의 인증/프로필/상태를 관리한다.
 * JPA와 무관한 순수 POJO로 구현되어 비즈니스 로직만 포함한다.
 */
@Getter
@Builder
public class User {

    private Long id;
    private String email;
    private String nickname;
    private String phoneNumber;
    private OAuthProvider provider;
    private String providerId;
    private UserRole role;
    private int warningCount;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 배송지 정보
    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingPostalCode;
    private String shippingAddress;
    private String shippingAddressDetail;

    // 계좌 정보 (판매 대금 수령용)
    private String bankName;
    private String accountNumber;
    private String accountHolder;

    private static final int MAX_WARNING_COUNT = 3;

    // ========== 팩토리 메서드 ==========

    /**
     * 신규 사용자를 생성한다.
     * OAuth 인증 성공 후 최초 가입 시 호출된다.
     * nickname, phoneNumber는 null로 생성되어 온보딩에서 설정한다.
     *
     * @param email      OAuth Provider에서 제공한 이메일
     * @param provider   OAuth Provider 종류
     * @param providerId Provider 고유 사용자 ID
     * @param role       사용자 역할 (USER 또는 ADMIN)
     * @return 생성된 User 도메인 객체
     */
    public static User create(String email, OAuthProvider provider, String providerId, UserRole role) {
        return User.builder()
                .email(email)
                .provider(provider)
                .providerId(providerId)
                .role(role)
                .warningCount(0)
                .isActive(true)
                .build();
    }

    /**
     * DB에서 복원된 User를 재구성한다.
     * Mapper에서 Entity → Domain 변환 시 사용한다.
     *
     * @return Builder 인스턴스 (모든 필드를 직접 설정)
     */
    public static UserBuilder reconstitute() {
        return User.builder();
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 사용자가 차단 상태인지 확인한다.
     * 경고 3회 이상이거나 비활성화된 사용자는 차단된다.
     *
     * @return 차단 여부
     */
    public boolean isBlocked() {
        return warningCount >= MAX_WARNING_COUNT || !isActive;
    }

    /**
     * 온보딩 완료 여부를 확인한다.
     * 닉네임과 전화번호가 모두 설정되어야 온보딩 완료이다.
     *
     * @return 온보딩 완료 여부
     */
    public boolean isOnboarded() {
        return nickname != null && phoneNumber != null;
    }

    /**
     * 온보딩을 완료한다.
     * 닉네임과 전화번호를 설정하여 사용자 프로필을 완성한다.
     *
     * @param nickname    닉네임 (2~20자, UK)
     * @param phoneNumber 전화번호 (010-XXXX-XXXX, UK, 변경 불가)
     */
    public void completeOnboarding(String nickname, String phoneNumber) {
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
    }

    /**
     * 닉네임을 변경한다.
     * 변경 횟수 제한 없음. UK 중복 검사는 서비스 레이어에서 처리한다.
     *
     * @param nickname 새로운 닉네임
     */
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 계정을 비활성화한다. (Soft Delete)
     * 비활성화된 계정으로는 재로그인이 차단된다.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 경고를 부여한다.
     * 노쇼(결제 미이행) 시 호출된다.
     * 3회 이상 경고 시 isBlocked() = true가 된다.
     */
    public void addWarning() {
        this.warningCount++;
    }

    /**
     * 관리자 여부를 확인한다.
     *
     * @return ADMIN 역할이면 true
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /**
     * 사용자 역할을 변경한다.
     * ADMIN_EMAILS 환경변수 변경 시 역할을 동기화할 때 사용한다.
     *
     * @param role 새로운 역할
     */
    public void updateRole(UserRole role) {
        this.role = role;
    }

    /**
     * 배송지 정보를 업데이트한다.
     *
     * @param recipientName 수령인 이름
     * @param phone         연락처
     * @param postalCode    우편번호
     * @param address       주소
     * @param addressDetail 상세주소
     */
    public void updateShippingAddress(String recipientName, String phone, String postalCode,
                                      String address, String addressDetail) {
        this.shippingRecipientName = recipientName;
        this.shippingPhone = phone;
        this.shippingPostalCode = postalCode;
        this.shippingAddress = address;
        this.shippingAddressDetail = addressDetail;
    }

    /**
     * 배송지가 등록되어 있는지 확인한다.
     *
     * @return 배송지 등록 여부
     */
    public boolean hasShippingAddress() {
        return shippingRecipientName != null && shippingAddress != null;
    }

    /**
     * 계좌 정보를 업데이트한다.
     * 판매자가 판매 대금을 수령할 계좌를 등록한다.
     *
     * @param bankName      은행명
     * @param accountNumber 계좌번호
     * @param accountHolder 예금주
     */
    public void updateBankAccount(String bankName, String accountNumber, String accountHolder) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }

    /**
     * 계좌가 등록되어 있는지 확인한다.
     *
     * @return 계좌 등록 여부
     */
    public boolean hasBankAccount() {
        return bankName != null && accountNumber != null && accountHolder != null;
    }
}
