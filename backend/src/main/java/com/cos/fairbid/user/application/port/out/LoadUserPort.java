package com.cos.fairbid.user.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;

/**
 * 사용자 조회 아웃바운드 포트
 * 영속성 계층에서 사용자를 조회하는 인터페이스를 정의한다.
 */
public interface LoadUserPort {

    /**
     * ID로 사용자를 조회한다.
     */
    Optional<User> findById(Long userId);

    /**
     * 여러 ID로 사용자를 일괄 조회한다.
     * N+1 문제 방지를 위해 사용한다.
     */
    List<User> findAllByIds(Set<Long> ids);

    /**
     * OAuth Provider와 Provider ID로 사용자를 조회한다.
     * 로그인 시 기존 사용자 여부를 판단하는 데 사용된다.
     */
    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    /**
     * 닉네임 중복 여부를 확인한다.
     */
    boolean existsByNickname(String nickname);

    /**
     * 전화번호 중복 여부를 확인한다.
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * 유저 목록을 페이징하여 조회한다.
     * 관리자 기능에서 사용한다.
     *
     * @param keyword  검색어 (닉네임 또는 이메일, optional)
     * @param pageable 페이지 정보
     * @return 유저 목록
     */
    Page<User> findAll(String keyword, Pageable pageable);
}
