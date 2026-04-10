package com.cos.fairbid.auth.application.port.out;

import io.jsonwebtoken.Claims;

import com.cos.fairbid.user.domain.User;

/**
 * 토큰 생성 및 검증 아웃바운드 포트
 * JWT 등 토큰 관련 인프라 구현체를 추상화한다.
 */
public interface TokenProviderPort {

    /**
     * Access Token을 생성한다.
     *
     * @param user User 도메인 객체
     * @return Access Token 문자열
     */
    String generateAccessToken(User user);

    /**
     * Refresh Token을 생성한다.
     *
     * @param user User 도메인 객체
     * @return Refresh Token 문자열
     */
    String generateRefreshToken(User user);

    /**
     * Access Token을 검증하여 Claims를 반환한다.
     *
     * @param token JWT 토큰 문자열
     * @return Claims 객체
     */
    Claims validateToken(String token);

    /**
     * Refresh Token을 검증하여 userId를 반환한다.
     *
     * @param token Refresh Token 문자열
     * @return 사용자 ID
     */
    Long getUserIdFromRefreshToken(String token);

    /**
     * Access Token에서 사용자 ID를 추출한다.
     *
     * @param token JWT 토큰 문자열
     * @return 사용자 ID
     */
    Long getUserIdFromToken(String token);

    /**
     * Refresh Token의 만료 시간을 초 단위로 반환한다.
     *
     * @return Refresh Token 만료 시간 (초)
     */
    long getRefreshExpirationSeconds();
}
