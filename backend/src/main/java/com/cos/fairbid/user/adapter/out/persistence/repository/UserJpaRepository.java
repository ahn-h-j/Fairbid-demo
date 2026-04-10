package com.cos.fairbid.user.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cos.fairbid.user.adapter.out.persistence.entity.UserEntity;
import com.cos.fairbid.user.domain.OAuthProvider;

/**
 * User Spring Data JPA Repository
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    /**
     * OAuth Provider와 Provider ID로 사용자를 조회한다.
     * 로그인 시 기존 가입 여부를 판단하는 핵심 쿼리이다.
     */
    Optional<UserEntity> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    /**
     * 닉네임 중복 여부를 확인한다.
     */
    boolean existsByNickname(String nickname);

    /**
     * 전화번호 중복 여부를 확인한다.
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * 닉네임 또는 이메일로 유저를 검색한다.
     * 관리자 유저 목록 조회에 사용한다.
     */
    @Query("SELECT u FROM UserEntity u WHERE "
            + "(:keyword IS NULL OR :keyword = '' OR "
            + "u.nickname LIKE %:keyword% OR u.email LIKE %:keyword%)")
    Page<UserEntity> findAllByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
