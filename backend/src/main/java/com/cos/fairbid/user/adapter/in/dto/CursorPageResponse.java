package com.cos.fairbid.user.adapter.in.dto;

import java.util.List;
import java.util.function.Function;

import com.cos.fairbid.common.pagination.CursorPage;

/**
 * 커서 기반 페이지네이션 응답 DTO
 * 무한스크롤 UI에서 사용하는 커서 기반 페이지 래퍼이다.
 *
 * @param items      현재 페이지 항목들
 * @param nextCursor 다음 페이지 커서 (null이면 마지막 페이지)
 * @param hasNext    다음 페이지 존재 여부
 */
public record CursorPageResponse<T>(
        List<T> items,
        Long nextCursor,
        boolean hasNext
) {
    /**
     * CursorPage UseCase 결과를 응답 DTO로 변환한다.
     *
     * @param page   UseCase에서 반환된 CursorPage
     * @param mapper 각 항목을 응답 DTO로 변환하는 함수
     * @return 변환된 CursorPageResponse
     */
    public static <S, T> CursorPageResponse<T> from(CursorPage<S> page, Function<S, T> mapper) {
        List<T> items = page.items().stream().map(mapper).toList();
        return new CursorPageResponse<>(items, page.nextCursor(), page.hasNext());
    }
}
