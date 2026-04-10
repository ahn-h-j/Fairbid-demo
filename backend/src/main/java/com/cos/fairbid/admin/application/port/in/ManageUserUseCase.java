package com.cos.fairbid.admin.application.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.cos.fairbid.admin.application.dto.AdminUserResult;

/**
 * 관리자 유저 관리 UseCase
 * 관리자가 유저를 조회하고 관리한다.
 */
public interface ManageUserUseCase {

    /**
     * 유저 목록을 조회한다.
     *
     * @param keyword  검색어 (닉네임 또는 이메일, optional)
     * @param pageable 페이지 정보
     * @return 유저 목록
     */
    Page<AdminUserResult> getUserList(String keyword, Pageable pageable);
}
