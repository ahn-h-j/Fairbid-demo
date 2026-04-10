package com.cos.fairbid.admin.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.admin.application.dto.AdminUserResult;
import com.cos.fairbid.admin.application.port.in.ManageUserUseCase;
import com.cos.fairbid.user.application.port.out.LoadUserPort;

/**
 * 관리자 유저 관리 서비스
 * 유저 목록 조회 기능을 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService implements ManageUserUseCase {

    private final LoadUserPort loadUserPort;

    @Override
    public Page<AdminUserResult> getUserList(String keyword, Pageable pageable) {
        return loadUserPort.findAll(keyword, pageable)
                .map(AdminUserResult::from);
    }
}
