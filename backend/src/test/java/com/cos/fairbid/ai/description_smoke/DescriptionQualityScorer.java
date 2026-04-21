package com.cos.fairbid.ai.description_smoke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.cos.fairbid.ai.adapter.out.guardrail.rules.DescriptionQualityRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.HookRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PersonaRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.ReformatRule;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 스모크 게이트 자동 지표 계산기.
 *
 * <p>SPEC §19 옵션 B 스모크 게이트 정의:</p>
 * <ul>
 *   <li>가드레일 위반: {@link DescriptionQualityRule} / {@link HookRule} / {@link PersonaRule} / {@link ReformatRule} 실행 후 누적 위반 ruleId 리스트</li>
 *   <li>클리셰 빈도: {@link DescriptionQualityRule#BANNED_PHRASES} 14종이 설명에 몇 번 등장하는가</li>
 *   <li>memo 재복사율: 설명 라인 집합과 memo 라인 집합의 Jaccard similarity (라인 단위)</li>
 * </ul>
 *
 * <p>Spring 컨텍스트 없이 {@code new} 로 와이어링하며, 기존 규칙 클래스를 재사용한다.</p>
 */
public final class DescriptionQualityScorer {

    /** 클리셰 카운트용. {@link DescriptionQualityRule#BANNED_PHRASES} 와 동일해야 의미가 유지된다. */
    private static final List<String> BANNED_PHRASES = List.of(
            "합리적인 가격", "최저가", "특가", "한정 수량",
            "100% 정품 보장", "100프로 정품", "후회 없는 선택",
            "강력 추천", "이번 기회", "편리하게 사용 가능",
            "다양하게 활용", "그대로 전달드립니다",
            "절대 후회", "확실히 만족"
    );

    private final DescriptionQualityRule qualityRule = new DescriptionQualityRule();
    private final HookRule hookRule = new HookRule();
    private final PersonaRule personaRule = new PersonaRule();
    private final ReformatRule reformatRule = new ReformatRule();

    public AutomatedMetrics score(String description, AiAssistCommand command) {
        if (description == null) {
            description = "";
        }

        List<String> violations = runRules(description, command);
        int clicheCount = countClicheOccurrences(description);
        double jaccard = lineJaccard(description, command == null ? null : command.memo());

        return new AutomatedMetrics(violations, clicheCount, jaccard);
    }

    private List<String> runRules(String description, AiAssistCommand command) {
        AiAssistResult fakeResult = new AiAssistResult(
                new SuggestedPrices(0L, 0L, 0L),
                description);
        // ReformatRule 은 command.memo() 를 참조하므로 command 가 null 이면 스킵.
        AiAssistCommand safeCommand = command != null
                ? command
                : new AiAssistCommand(null, "", List.of());

        List<PriceItem> emptyItems = List.of();
        List<GuardrailViolation> all = new ArrayList<>();
        all.addAll(qualityRule.check(fakeResult, safeCommand, emptyItems));
        all.addAll(hookRule.check(fakeResult, safeCommand, emptyItems));
        all.addAll(personaRule.check(fakeResult, safeCommand, emptyItems));
        all.addAll(reformatRule.check(fakeResult, safeCommand, emptyItems));
        return all.stream().map(GuardrailViolation::ruleId).toList();
    }

    private int countClicheOccurrences(String description) {
        String lower = description.toLowerCase(Locale.ROOT);
        int total = 0;
        for (String phrase : BANNED_PHRASES) {
            String key = phrase.toLowerCase(Locale.ROOT);
            int idx = 0;
            while ((idx = lower.indexOf(key, idx)) >= 0) {
                total++;
                idx += key.length();
            }
        }
        return total;
    }

    private double lineJaccard(String description, String memo) {
        Set<String> descLines = normalizeLines(description);
        Set<String> memoLines = normalizeLines(memo);
        if (descLines.isEmpty() || memoLines.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(descLines);
        intersection.retainAll(memoLines);
        Set<String> union = new HashSet<>(descLines);
        union.addAll(memoLines);
        return (double) intersection.size() / union.size();
    }

    private Set<String> normalizeLines(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String raw : Arrays.asList(text.split("\\R"))) {
            String stripped = raw.trim();
            if (stripped.isEmpty()) {
                continue;
            }
            out.add(stripped);
        }
        return out;
    }

    public record AutomatedMetrics(
            List<String> guardrailViolations,
            int clicheCount,
            double reformatJaccard
    ) {
    }
}
