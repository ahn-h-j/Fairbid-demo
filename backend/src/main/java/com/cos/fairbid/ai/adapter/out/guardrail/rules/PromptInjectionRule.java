package com.cos.fairbid.ai.adapter.out.guardrail.rules;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.service.guardrail.InputGuardrailRule;

/**
 * 입력 가드레일 (HARD): 프롬프트 인젝션 패턴 탐지.
 *
 * memo 필드에서 인젝션으로 의심되는 패턴을 찾으면 요청을 차단한다.
 * 차단은 {@link com.cos.fairbid.ai.domain.exception.PromptInjectionDetectedException}
 * 를 throw 하는 방식 — InputGuardrailChain 이 규칙 결과를 보고 예외를 발생시킨다.
 *
 * **강하게 차단 (false positive 허용)**:
 * 보안 규칙이므로 의심스러우면 무조건 차단. 정상 사용자는 상품 설명만 쓰면 되므로
 * 이런 패턴과 겹칠 일이 거의 없다.
 */
@Component
public class PromptInjectionRule implements InputGuardrailRule {

    private static final String RULE_ID = "PROMPT_INJECTION";

    /**
     * 인젝션 의심 패턴 (한국어 + 영어). 대소문자 무시, 부분 매칭.
     * 하나라도 걸리면 즉시 차단.
     */
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            // === 한국어: 지시 무시 / 덮어쓰기 ===
            compile("기존\\s*(지시|프롬프트|명령|규칙|설정)"),
            compile("이전\\s*(지시|프롬프트|명령|내용|대화|설정)"),
            compile("위의?\\s*(지시|프롬프트|명령|내용|규칙)"),
            compile("처음\\s*(지시|프롬프트|설정)"),
            compile("(이전|기존|위의|처음)[^\\n]{0,10}(무시|잊|삭제|버리|덮어)"),
            compile("(무시|잊|삭제)[^\\n]{0,10}(지시|프롬프트|명령|규칙|내용)"),
            compile("(새로운|새)\\s*(지시|명령|역할|규칙)"),

            // === 한국어: 역할 탈취 ===
            compile("너는?\\s*이제"),
            compile("당신은\\s*이제"),
            compile("지금부터\\s*(너|당신|AI)(는|은)?"),
            compile("(역할|페르소나)\\s*(변경|바꾸|전환)"),
            compile("(해커|관리자|admin|시스템\\s*관리자)\\s*(처럼|역할|로|이다)"),

            // === 한국어: 시스템 프롬프트 탈취 ===
            compile("(시스템|system)\\s*(프롬프트|메시지|지시|명령)"),
            compile("프롬프트\\s*(보여|출력|공개|노출|알려)"),
            compile("설정\\s*(보여|출력|공개)"),

            // === 한국어: 정보 노출 요구 ===
            compile("(DB|database|데이터베이스)\\s*(쿼리|조회|덤프|출력)"),
            compile("(API\\s*키|api[-_]?key|secret|토큰|비밀번호|password)"),
            compile("(내부|internal)\\s*(데이터|정보|구조)"),

            // === 영어: 고전 패턴 ===
            compile("(?i)ignore\\s+(previous|above|all|prior)"),
            compile("(?i)forget\\s+(everything|all|previous|above)"),
            compile("(?i)disregard\\s+(previous|above|all|instructions)"),
            compile("(?i)override\\s+(instructions|prompt|rules)"),
            compile("(?i)you\\s+are\\s+now"),
            compile("(?i)act\\s+as\\s+"),
            compile("(?i)pretend\\s+(to\\s+be|you\\s+are)"),
            compile("(?i)new\\s+(instructions|role|task)\\s*:"),
            compile("(?i)system\\s+prompt"),
            compile("(?i)reveal\\s+(prompt|instructions|system)"),
            compile("(?i)(dump|show|print|leak|output)\\s+(the\\s+)?(prompt|system|instructions)"),

            // === 영어: 역할 탈취 ===
            compile("(?i)(you|claude|assistant)\\s+(is|are)\\s+(no\\s+longer|now)"),
            compile("(?i)jailbreak"),
            compile("(?i)DAN\\s+mode"),
            compile("(?i)developer\\s+mode"),

            // === 델리미터 주입 (assistant/user 턴 조작) ===
            compile("(?i)\\{\\{.*\\}\\}"),
            compile("<\\|.*\\|>"),
            compile("```\\s*(system|assistant|user)"),
            compile("(?i)\\[system\\]"),
            compile("(?i)\\[/system\\]"),
            compile("\\bassistant\\s*:\\s*"),
            compile("\\bsystem\\s*:\\s*"),

            // === 코드 실행 유도 ===
            compile("(?i)execute\\s+(code|script|command)"),
            compile("(?i)run\\s+this\\s+(code|script|command)"),
            compile("(코드|스크립트|명령)\\s*(실행|수행)")
    );

    private static Pattern compile(String regex) {
        return Pattern.compile(regex);
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<String> check(AiAssistCommand command, List<PriceItem> priceItems) {
        if (command.memo() == null || command.memo().isBlank()) {
            return List.of();
        }

        String memo = command.memo();

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(memo).find()) {
                // 어떤 패턴에 걸렸는지는 남기되, 사용자에게는 노출하지 않는다
                return List.of("PROMPT_INJECTION_DETECTED: " + pattern.pattern());
            }
        }

        return List.of();
    }
}
