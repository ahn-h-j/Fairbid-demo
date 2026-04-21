package com.cos.fairbid.ai.description_smoke;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cos.fairbid.ai.description_smoke.DescriptionQualityScorer.AutomatedMetrics;
import com.cos.fairbid.ai.description_smoke.DescriptionSmokeRunnerTest.CaseRecord;
import com.cos.fairbid.ai.description_smoke.DescriptionSmokeRunnerTest.Generation;
import com.cos.fairbid.ai.description_smoke.LlmJudge.AbsoluteScores;

/**
 * 스모크 게이트 결과 집계 리포트 렌더러.
 *
 * <p>SPEC §19 판정 기준 적용:</p>
 * <ul>
 *   <li>쌍비교 Gemini 승률 (TIE 제외 유효표 기준) ≥ 40% → Port 재설계 진행</li>
 *   <li>자동 지표 Gemini 가드레일 위반율이 Claude 대비 +10pp 이상 악화 → 롤백</li>
 *   <li>특정 항목 승률 &lt; 25% → 해당 항목 Gemini 프롬프트 보강 후 재측정</li>
 * </ul>
 */
final class SmokeReportRenderer {

    private static final List<String> CRITERIA = List.of(
            "hook", "no_spec_dump", "hidden_value", "persona_clarity", "no_reformat");

    private SmokeReportRenderer() {
    }

    static String render(List<CaseRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Description Smoke Gate Report\n\n");
        sb.append("- Cases: ").append(records.size()).append("\n\n");

        appendAutomatedSummary(sb, records);
        appendAbsoluteSummary(sb, records);
        appendPairwiseSummary(sb, records);
        appendVerdict(sb, records);
        appendPerCase(sb, records);
        return sb.toString();
    }

    private static void appendAutomatedSummary(StringBuilder sb, List<CaseRecord> records) {
        sb.append("## Automated Metrics\n\n");
        sb.append("| 지표 | Claude | Gemini 2.5 Pro |\n");
        sb.append("|---|---|---|\n");

        int total = records.size();
        int claudeViolations = 0;
        int geminiViolations = 0;
        double claudeClicheAvg = 0;
        double geminiClicheAvg = 0;
        double claudeJaccardAvg = 0;
        double geminiJaccardAvg = 0;
        int claudeWithMetrics = 0;
        int geminiWithMetrics = 0;

        for (CaseRecord r : records) {
            AutomatedMetrics cm = r.claude() == null ? null : r.claude().automated();
            AutomatedMetrics gm = r.gemini() == null ? null : r.gemini().automated();
            if (cm != null) {
                claudeWithMetrics++;
                if (!cm.guardrailViolations().isEmpty()) {
                    claudeViolations++;
                }
                claudeClicheAvg += cm.clicheCount();
                claudeJaccardAvg += cm.reformatJaccard();
            }
            if (gm != null) {
                geminiWithMetrics++;
                if (!gm.guardrailViolations().isEmpty()) {
                    geminiViolations++;
                }
                geminiClicheAvg += gm.clicheCount();
                geminiJaccardAvg += gm.reformatJaccard();
            }
        }

        sb.append("| 가드레일 위반율 | ")
                .append(formatRate(claudeViolations, claudeWithMetrics)).append(" | ")
                .append(formatRate(geminiViolations, geminiWithMetrics)).append(" |\n");
        sb.append("| 클리셰 평균 | ")
                .append(formatAvg(claudeClicheAvg, claudeWithMetrics)).append(" | ")
                .append(formatAvg(geminiClicheAvg, geminiWithMetrics)).append(" |\n");
        sb.append("| memo 재복사(Jaccard) 평균 | ")
                .append(formatAvg(claudeJaccardAvg, claudeWithMetrics)).append(" | ")
                .append(formatAvg(geminiJaccardAvg, geminiWithMetrics)).append(" |\n");
        sb.append("| 성공 케이스 | ").append(claudeWithMetrics).append("/").append(total)
                .append(" | ").append(geminiWithMetrics).append("/").append(total).append(" |\n\n");
    }

    private static void appendAbsoluteSummary(StringBuilder sb, List<CaseRecord> records) {
        sb.append("## LLM-Judge Absolute (5 criteria × 1~5점)\n\n");
        sb.append("| 기준 | Claude 평균 | Gemini 평균 |\n");
        sb.append("|---|---|---|\n");

        for (String criterion : CRITERIA) {
            double claudeSum = 0;
            double geminiSum = 0;
            int claudeN = 0;
            int geminiN = 0;
            for (CaseRecord r : records) {
                AbsoluteScores ca = r.claude() == null ? null : r.claude().llmJudgeAbsolute();
                AbsoluteScores ga = r.gemini() == null ? null : r.gemini().llmJudgeAbsolute();
                if (ca != null && ca.scores().get(criterion) != null) {
                    claudeSum += ca.scores().get(criterion).score();
                    claudeN++;
                }
                if (ga != null && ga.scores().get(criterion) != null) {
                    geminiSum += ga.scores().get(criterion).score();
                    geminiN++;
                }
            }
            sb.append("| ").append(criterion).append(" | ")
                    .append(formatAvg(claudeSum, claudeN)).append(" | ")
                    .append(formatAvg(geminiSum, geminiN)).append(" |\n");
        }
        sb.append("\n");
    }

    private static void appendPairwiseSummary(StringBuilder sb, List<CaseRecord> records) {
        sb.append("## LLM-Judge Pairwise (순서 랜덤화 후 생성자별 복구)\n\n");
        sb.append("| 기준 | Claude 승 | Gemini 승 | TIE | Gemini 승률(비-TIE) |\n");
        sb.append("|---|---|---|---|---|\n");

        for (String criterion : CRITERIA) {
            int claudeWin = 0;
            int geminiWin = 0;
            int tie = 0;
            for (CaseRecord r : records) {
                Map<String, String> pw = r.pairwiseByGenerator();
                if (pw == null) {
                    continue;
                }
                String choice = pw.get(criterion);
                if ("claude".equals(choice)) {
                    claudeWin++;
                } else if ("gemini".equals(choice)) {
                    geminiWin++;
                } else {
                    tie++;
                }
            }
            int decisive = claudeWin + geminiWin;
            String geminiRate = decisive == 0 ? "-"
                    : String.format(Locale.ROOT, "%.1f%%", 100.0 * geminiWin / decisive);
            sb.append("| ").append(criterion).append(" | ")
                    .append(claudeWin).append(" | ")
                    .append(geminiWin).append(" | ")
                    .append(tie).append(" | ")
                    .append(geminiRate).append(" |\n");
        }
        sb.append("\n");
    }

    private static void appendVerdict(StringBuilder sb, List<CaseRecord> records) {
        int claudeWinAll = 0;
        int geminiWinAll = 0;
        int tieAll = 0;
        for (CaseRecord r : records) {
            Map<String, String> pw = r.pairwiseByGenerator();
            if (pw == null) {
                continue;
            }
            for (String criterion : CRITERIA) {
                String choice = pw.get(criterion);
                if ("claude".equals(choice)) {
                    claudeWinAll++;
                } else if ("gemini".equals(choice)) {
                    geminiWinAll++;
                } else {
                    tieAll++;
                }
            }
        }
        int decisiveAll = claudeWinAll + geminiWinAll;
        double geminiOverallRate = decisiveAll == 0 ? 0.0 : (double) geminiWinAll / decisiveAll;

        sb.append("## Verdict (SPEC §19 기준)\n\n");
        sb.append("- 전체 쌍비교 Gemini 승률(비-TIE): ")
                .append(String.format(Locale.ROOT, "%.1f%%", geminiOverallRate * 100))
                .append(" (claude ").append(claudeWinAll)
                .append(" / gemini ").append(geminiWinAll)
                .append(" / tie ").append(tieAll).append(")\n");
        if (geminiOverallRate >= 0.40) {
            sb.append("- **판정**: Port 재설계 진행 (≥ 40%)\n");
        } else if (geminiOverallRate >= 0.25) {
            sb.append("- **판정**: 특정 항목 Gemini 프롬프트 보강 후 재측정 (25~40%)\n");
        } else {
            sb.append("- **판정**: 현행 Claude 단독 유지, Port 재설계 보류 (< 25%)\n");
        }
        sb.append("\n");
    }

    private static void appendPerCase(StringBuilder sb, List<CaseRecord> records) {
        sb.append("## Per-Case\n\n");
        for (CaseRecord r : records) {
            sb.append("### ").append(r.caseId()).append("\n\n");
            appendGeneration(sb, "Claude", r.claude());
            appendGeneration(sb, "Gemini 2.5 Pro", r.gemini());
            if (r.pairwiseByGenerator() != null) {
                sb.append("- Pairwise: ").append(r.pairwiseByGenerator()).append("\n");
                sb.append("- Order: ").append(r.pairwiseOrder()).append("\n");
            }
            sb.append("\n");
        }
    }

    private static void appendGeneration(StringBuilder sb, String label, Generation gen) {
        if (gen == null) {
            sb.append("- ").append(label).append(": (없음)\n");
            return;
        }
        if (gen.exceptionType() != null) {
            sb.append("- ").append(label).append(": 예외 ")
                    .append(gen.exceptionType()).append(" — ").append(gen.exceptionMessage()).append("\n");
            return;
        }
        AutomatedMetrics m = gen.automated();
        AbsoluteScores a = gen.llmJudgeAbsolute();
        sb.append("- ").append(label).append(": confidence=").append(gen.confidence()).append("\n");
        if (m != null) {
            sb.append("  - 자동: 위반=").append(m.guardrailViolations())
                    .append(", 클리셰=").append(m.clicheCount())
                    .append(", Jaccard=").append(String.format(Locale.ROOT, "%.2f", m.reformatJaccard()))
                    .append("\n");
        }
        if (a != null) {
            sb.append("  - Judge 절대: total=").append(a.total()).append("/25\n");
        }
    }

    private static String formatRate(int hit, int total) {
        if (total == 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%d/%d (%.1f%%)", hit, total, 100.0 * hit / total);
    }

    private static String formatAvg(double sum, int n) {
        if (n == 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", sum / n);
    }
}
