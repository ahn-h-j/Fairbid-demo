package com.cos.fairbid.user.application.service;

import com.cos.fairbid.auth.application.port.out.RefreshTokenPort;
import com.cos.fairbid.auth.application.port.out.TokenProviderPort;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.user.application.port.in.CheckNicknameUseCase;
import com.cos.fairbid.user.application.port.in.CompleteOnboardingUseCase;
import com.cos.fairbid.user.application.port.in.DeactivateAccountUseCase;
import com.cos.fairbid.user.application.port.in.UpdateBankAccountUseCase;
import com.cos.fairbid.user.application.port.in.UpdateNicknameUseCase;
import com.cos.fairbid.user.application.port.in.UpdateShippingAddressUseCase;
import com.cos.fairbid.user.application.port.in.GetMyProfileUseCase;
import com.cos.fairbid.user.application.port.in.GetTradeStatsUseCase;
import com.cos.fairbid.user.application.port.out.LoadUserPort;
import com.cos.fairbid.user.application.port.out.SaveUserPort;
import com.cos.fairbid.user.domain.User;
import com.cos.fairbid.user.domain.exception.AlreadyOnboardedException;
import com.cos.fairbid.user.domain.exception.NicknameDuplicateException;
import com.cos.fairbid.user.domain.exception.PhoneNumberDuplicateException;
import com.cos.fairbid.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 서비스
 * 온보딩, 프로필 조회/수정, 닉네임 확인, 회원 탈퇴를 처리한다.
 *
 * JWT 재발급이 필요한 경우 (온보딩, 닉네임 수정):
 * - Access Token의 nickname, onboarded 클레임이 변경되므로 새 토큰을 발급한다.
 * - Refresh Token도 Rotation 정책에 따라 함께 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements CompleteOnboardingUseCase, CheckNicknameUseCase,
        GetMyProfileUseCase, UpdateNicknameUseCase, DeactivateAccountUseCase,
        UpdateShippingAddressUseCase, UpdateBankAccountUseCase, GetTradeStatsUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final TokenProviderPort tokenProviderPort;
    private final RefreshTokenPort refreshTokenPort;
    private final TradeRepositoryPort tradeRepositoryPort;

    /**
     * 온보딩을 완료한다.
     *
     * 흐름:
     * 1. 사용자 조회
     * 2. 이미 온보딩 완료 상태 체크
     * 3. 닉네임 중복 확인
     * 4. 전화번호 중복 확인
     * 5. 온보딩 정보 저장
     * 6. 새 JWT 발급 (onboarded=true)
     */
    @Override
    @Transactional
    public OnboardingResult completeOnboarding(Long userId, String nickname, String phoneNumber) {
        // 1. 사용자 조회
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        // 2. 이미 온보딩 완료 상태 체크
        if (user.isOnboarded()) {
            throw AlreadyOnboardedException.create();
        }

        // 3. 닉네임 중복 확인
        if (loadUserPort.existsByNickname(nickname)) {
            throw NicknameDuplicateException.of(nickname);
        }

        // 4. 전화번호 중복 확인
        if (loadUserPort.existsByPhoneNumber(phoneNumber)) {
            throw PhoneNumberDuplicateException.create();
        }

        // 5. 온보딩 정보 저장
        user.completeOnboarding(nickname, phoneNumber);
        user = saveUserPort.save(user);
        log.info("온보딩 완료: userId={}", userId);

        // 6. 새 JWT 발급 (onboarded=true, nickname 포함)
        return reissueTokens(user);
    }

    /**
     * 닉네임 사용 가능 여부를 확인한다.
     */
    @Override
    public boolean isAvailable(String nickname) {
        return !loadUserPort.existsByNickname(nickname);
    }

    /**
     * 내 프로필 정보를 조회한다.
     */
    @Override
    public User getMyProfile(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));
    }

    /**
     * 닉네임을 수정한다.
     *
     * 흐름:
     * 1. 사용자 조회
     * 2. 닉네임 중복 확인
     * 3. 닉네임 변경 + 저장
     * 4. 새 JWT 발급 (nickname 변경 반영)
     */
    @Override
    @Transactional
    public UpdateResult updateNickname(Long userId, String nickname) {
        // 1. 사용자 조회
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        // 2. 닉네임 중복 확인 (현재 닉네임과 동일하면 스킵)
        if (!nickname.equals(user.getNickname()) && loadUserPort.existsByNickname(nickname)) {
            throw NicknameDuplicateException.of(nickname);
        }

        // 3. 닉네임 변경 + 저장
        user.updateNickname(nickname);
        user = saveUserPort.save(user);
        log.info("닉네임 수정: userId={}", userId);

        // 4. 새 JWT 발급
        String accessToken = tokenProviderPort.generateAccessToken(user);
        String refreshToken = tokenProviderPort.generateRefreshToken(user);
        refreshTokenPort.save(userId, refreshToken, tokenProviderPort.getRefreshExpirationSeconds());

        return new UpdateResult(accessToken, refreshToken);
    }

    /**
     * 계정을 비활성화한다. (Soft Delete)
     *
     * 흐름:
     * 1. 사용자 조회
     * 2. 비활성화 처리
     * 3. Redis Refresh Token 삭제 (세션 무효화)
     */
    @Override
    @Transactional
    public void deactivate(Long userId) {
        // 1. 사용자 조회
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        // 2. 비활성화 처리
        user.deactivate();
        saveUserPort.save(user);

        // 3. Redis Refresh Token 삭제
        refreshTokenPort.delete(userId);
        log.info("회원 탈퇴: userId={}", userId);
    }

    /**
     * 배송지 정보를 수정한다.
     */
    @Override
    @Transactional
    public User updateShippingAddress(Long userId, String recipientName, String phone,
                                      String postalCode, String address, String addressDetail) {
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        user.updateShippingAddress(recipientName, phone, postalCode, address, addressDetail);
        user = saveUserPort.save(user);
        log.info("배송지 수정: userId={}", userId);

        return user;
    }

    /**
     * 사용자의 거래 통계를 조회한다.
     */
    @Override
    public TradeStats getTradeStats(Long userId) {
        return new TradeStats(
                tradeRepositoryPort.countCompletedSales(userId),
                tradeRepositoryPort.countCompletedPurchases(userId),
                tradeRepositoryPort.sumCompletedSalesAmount(userId),
                tradeRepositoryPort.sumCompletedPurchaseAmount(userId)
        );
    }

    /**
     * 계좌 정보를 수정한다.
     * 판매자가 판매 대금을 수령할 계좌를 등록/수정한다.
     */
    @Override
    @Transactional
    public User updateBankAccount(Long userId, String bankName, String accountNumber, String accountHolder) {
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        user.updateBankAccount(bankName, accountNumber, accountHolder);
        user = saveUserPort.save(user);
        log.info("계좌 정보 수정: userId={}", userId);

        return user;
    }

    /**
     * 새 Access Token + Refresh Token을 발급하고 Redis에 저장한다.
     */
    private OnboardingResult reissueTokens(User user) {
        String accessToken = tokenProviderPort.generateAccessToken(user);
        String refreshToken = tokenProviderPort.generateRefreshToken(user);
        refreshTokenPort.save(user.getId(), refreshToken, tokenProviderPort.getRefreshExpirationSeconds());
        return new OnboardingResult(accessToken, refreshToken);
    }
}
