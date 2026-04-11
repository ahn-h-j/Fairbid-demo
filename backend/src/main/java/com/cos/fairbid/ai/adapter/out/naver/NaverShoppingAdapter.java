package com.cos.fairbid.ai.adapter.out.naver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.adapter.out.naver.dto.NaverCafeResponse;
import com.cos.fairbid.ai.adapter.out.naver.dto.NaverShoppingResponse;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.port.out.PriceSearchPort;

/**
 * 네이버 쇼핑 검색 API 어댑터 ({@link PriceSearchPort} 구현).
 *
 * v2 Phase 1 — Anthropic web_search 도구를 우리 검색 파이프라인으로 대체.
 * Claude 호출 전에 이 어댑터로 시세 데이터를 직접 모아 user message 에 주입한다.
 *
 * 설계:
 * - RestClient 동기 호출 (기존 OAuth/Anthropic 어댑터와 동일 패턴)
 * - 외부 API 장애 시 빈 리스트 반환 — 호출측이 시세 hint 없이라도 진행할 수 있게 한다
 *   (Claude 단독 추론으로 fallback). 도메인 예외로 변환하지 않는다.
 * - 응답 title 의 {@code <b>...</b>} 마크업 제거
 * - hprice "0" 은 null 로 정규화 (단일 상품 케이스)
 */
@Slf4j
@Component
public class NaverShoppingAdapter implements PriceSearchPort {

    private static final String SHOP_SEARCH_PATH = "/v1/search/shop.json";
    private static final String CAFE_SEARCH_PATH = "/v1/search/cafearticle.json";
    private static final int MAX_LIMIT = 100;
    private static final int CAFE_DISPLAY = 5; // 카페글은 5건만 (본문이 길어서 토큰 절약)

    private final RestClient restClient;
    private final NaverSearchProperties properties;

    public NaverShoppingAdapter(NaverSearchProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.properties = properties;
    }

    @Override
    public List<PriceItem> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            log.debug("네이버 검색 keyword 비어있음 - 빈 리스트 반환");
            return Collections.emptyList();
        }
        if (properties.getClientId() == null || properties.getClientId().isBlank()) {
            log.warn("NAVER_CLIENT_ID 미설정 - 시세 검색 비활성화 상태로 간주");
            return Collections.emptyList();
        }

        int display = Math.min(Math.max(1, limit), MAX_LIMIT);
        String uri = UriComponentsBuilder.fromPath(SHOP_SEARCH_PATH)
                .queryParam("query", keyword)
                .queryParam("display", display)
                .queryParam("sort", "sim") // 정확도순 (default). "asc"/"dsc" 는 가격순.
                .build()
                .toUriString();

        try {
            NaverShoppingResponse response = restClient.get()
                    .uri(uri)
                    .header("X-Naver-Client-Id", properties.getClientId())
                    .header("X-Naver-Client-Secret", properties.getClientSecret())
                    .retrieve()
                    .body(NaverShoppingResponse.class);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                log.info("네이버 쇼핑 검색 결과 없음 - keyword={}", keyword);
                return Collections.emptyList();
            }

            List<PriceItem> result = new ArrayList<>(response.items().size() + CAFE_DISPLAY);
            for (NaverShoppingResponse.Item item : response.items()) {
                PriceItem mapped = mapToPriceItem(item);
                if (mapped != null) {
                    result.add(mapped);
                }
            }
            log.info("네이버 쇼핑 검색 - keyword={}, total={}, mapped={}",
                    keyword, response.total(), result.size());

            // 카페 검색 (중고 거래글) — 쇼핑 결과에 합친다
            List<PriceItem> cafeItems = searchCafe(keyword + " 중고");
            result.addAll(cafeItems);

            return result;

        } catch (RestClientResponseException e) {
            log.warn("네이버 쇼핑 API HTTP 에러 - status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (ResourceAccessException e) {
            log.warn("네이버 쇼핑 API 네트워크 오류: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 네이버 응답 항목을 PriceItem 으로 변환.
     * lprice 파싱 실패한 항목은 버린다 (가격 정보 없으면 시세 hint 로 무가치).
     */
    private PriceItem mapToPriceItem(NaverShoppingResponse.Item item) {
        long lprice;
        try {
            lprice = Long.parseLong(item.lprice());
        } catch (NumberFormatException e) {
            log.debug("lprice 파싱 실패 - skip: {}", item.lprice());
            return null;
        }

        Long hprice = null;
        if (item.hprice() != null && !item.hprice().isBlank() && !"0".equals(item.hprice())) {
            try {
                hprice = Long.parseLong(item.hprice());
            } catch (NumberFormatException ignore) {
                // 단일 상품의 경우 "0" 또는 빈 문자열 — null 유지
            }
        }

        return new PriceItem(
                stripHtmlTags(item.title()),
                lprice,
                hprice,
                nullToEmpty(item.mallName()),
                nullToEmpty(item.brand()),
                buildCategoryPath(item)
        );
    }

    /**
     * 네이버 응답 title 의 {@code <b>...</b>} 마크업과 HTML 엔티티를 제거한다.
     */
    private String stripHtmlTags(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    /**
     * category1~4 를 " > " 로 합친다. 빈 값은 건너뛴다.
     */
    private String buildCategoryPath(NaverShoppingResponse.Item item) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, item.category1());
        appendIfPresent(sb, item.category2());
        appendIfPresent(sb, item.category3());
        appendIfPresent(sb, item.category4());
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String segment) {
        if (segment == null || segment.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" > ");
        }
        sb.append(segment);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 네이버 카페 검색으로 중고 거래글을 가져온다.
     * description(글 본문 발췌)에 실 거래가가 포함되어 있으며, Claude 가 이를 파악한다.
     * 장애 시 빈 리스트 — 쇼핑 결과만으로 진행.
     */
    private List<PriceItem> searchCafe(String keyword) {
        String uri = UriComponentsBuilder.fromPath(CAFE_SEARCH_PATH)
                .queryParam("query", keyword)
                .queryParam("display", CAFE_DISPLAY)
                .queryParam("sort", "sim")
                .build()
                .toUriString();

        try {
            NaverCafeResponse response = restClient.get()
                    .uri(uri)
                    .header("X-Naver-Client-Id", properties.getClientId())
                    .header("X-Naver-Client-Secret", properties.getClientSecret())
                    .retrieve()
                    .body(NaverCafeResponse.class);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                return Collections.emptyList();
            }

            List<PriceItem> result = new ArrayList<>(response.items().size());
            for (NaverCafeResponse.Item item : response.items()) {
                String title = stripHtmlTags(item.title());
                String desc = stripHtmlTags(item.description());
                if (title.isEmpty() && desc.isEmpty()) {
                    continue;
                }
                result.add(new PriceItem(
                        title, 0, null,
                        nullToEmpty(item.cafename()), "", "",
                        desc
                ));
            }
            log.info("네이버 카페 검색 - keyword={}, total={}, mapped={}",
                    keyword, response.total(), result.size());
            return result;

        } catch (RestClientResponseException e) {
            log.warn("네이버 카페 API HTTP 에러 - status={}", e.getStatusCode().value());
            return Collections.emptyList();
        } catch (ResourceAccessException e) {
            log.warn("네이버 카페 API 네트워크 오류: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
