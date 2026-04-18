package com.cos.fairbid.ai.benchmark.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.runner.RawResult;
import com.cos.fairbid.ai.benchmark.score.PassAtK;
import com.cos.fairbid.ai.benchmark.score.WilsonCI;

/**
 * RawResult 리스트를 {@link ModelReport.Summary} 로 집계하는 순수 함수.
 *
 * <h3>지표 정의</h3>
 * <ul>
 *   <li><b>Strict PASS rate</b>: strict == 1.0인 run 비율 (전체 run 기준).</li>
 *   <li><b>Mean Score100</b>: 0~100 연속 점수의 run 평균(예외 run 제외).</li>
 *   <li><b>Mean IoU</b>: 예외를 제외한 run의 IoU 평균.</li>
 *   <li><b>pass@1</b>: 케이스별 strict c/n 의 케이스 평균.</li>
 *   <li><b>pass@3 / pass^3</b>: 케이스별 계산 후 평균 (runsPerCase &lt; 3 이면 null).</li>
 *   <li><b>Wilson CI</b>: 전체 run 수 대비 strict 수로 95% 신뢰구간.</li>
 * </ul>
 *
 * <p>카테고리/태그 버킷은 해당 케이스의 모든 run을 한 버킷으로 모은다.
 * {@code bottom3}는 케이스별 meanScore100 오름차순(동률이면 strictPassRate 오름차순).</p>
 */
public final class ReportAggregator {

    /** pass@k / pass^k 의 k. LLM 평가 관례를 따라 3 고정. */
    private static final int K = 3;

    private ReportAggregator() {
    }

    /**
     * @param model 모델 라벨(리포트 타이틀용)
     * @param results 단일 모델의 RawResult 전체
     * @param cases 동일 GoldenDataset (카테고리/태그 메타데이터 참조용)
     */
    public static ModelReport.Summary aggregate(
            String model,
            List<RawResult> results,
            List<GoldenCase> cases) {

        if (results.isEmpty()) {
            return emptySummary(model);
        }

        // caseId → GoldenCase 메타 조회 맵
        Map<String, GoldenCase> caseById = cases.stream()
                .collect(Collectors.toMap(GoldenCase::id, c -> c, (a, b) -> a));

        // caseId → 해당 케이스의 모든 run
        Map<String, List<RawResult>> resultsByCase = results.stream()
                .collect(Collectors.groupingBy(RawResult::caseId));

        int totalRuns = results.size();
        int strictPassRuns = countStrict(results);
        int exceptions = (int) results.stream().filter(r -> r.exceptionType() != null).count();

        double strictPassRate = (double) strictPassRuns / totalRuns;
        double meanScore100 = meanScore(results);
        double meanIou = meanIouOnStrictPass(results);

        // 케이스별 pass@1 평균 = 마이크로가 아니라 매크로 평균 (케이스 간 동등가중)
        double passAt1 = resultsByCase.values().stream()
                .mapToDouble(caseRuns -> PassAtK.passAt1(countStrict(caseRuns), caseRuns.size()))
                .average()
                .orElse(0.0);

        Double passAt3 = null;
        Double passPower3 = null;
        // k=3 집계는 모든 케이스에서 run >= 3 일 때만 의미 있음.
        boolean allCasesHaveK = resultsByCase.values().stream().allMatch(r -> r.size() >= K);
        if (allCasesHaveK) {
            passAt3 = resultsByCase.values().stream()
                    .mapToDouble(cr -> PassAtK.passAtK(countStrict(cr), cr.size(), K))
                    .average()
                    .orElse(0.0);
            passPower3 = resultsByCase.values().stream()
                    .mapToDouble(cr -> PassAtK.passPowerK(countStrict(cr), cr.size(), K))
                    .average()
                    .orElse(0.0);
        }

        WilsonCI.Bounds strictCI = WilsonCI.compute(strictPassRuns, totalRuns);

        Map<String, ModelReport.BucketStats> byCategory = aggregateByCategory(resultsByCase, caseById);
        Map<String, ModelReport.BucketStats> byTag = aggregateByTag(resultsByCase, caseById);
        List<ModelReport.CaseStats> caseStatsAll = buildCaseStats(resultsByCase, caseById);
        List<ModelReport.CaseStats> bottom3 = pickBottom3(caseStatsAll);
        List<ModelReport.ExceptionEntry> exceptionList = buildExceptionList(results);

        return new ModelReport.Summary(
                model,
                resultsByCase.size(),
                totalRuns,
                strictPassRuns,
                exceptions,
                strictPassRate,
                meanScore100,
                meanIou,
                passAt1,
                passAt3,
                passPower3,
                strictCI,
                byCategory,
                byTag,
                bottom3,
                exceptionList);
    }

    private static ModelReport.Summary emptySummary(String model) {
        return new ModelReport.Summary(
                model, 0, 0, 0, 0,
                0.0, 0.0, 0.0, 0.0, null, null,
                new WilsonCI.Bounds(0.0, 1.0),
                Map.of(), Map.of(), List.of(), List.of());
    }

    private static int countStrict(List<RawResult> results) {
        return (int) results.stream()
                .filter(r -> r.strictPass() != null && r.strictPass() == 1.0)
                .count();
    }

    /** 예외 run(score100==null)은 평균에서 제외. 전부 null이면 0.0. */
    private static double meanScore(List<RawResult> results) {
        return results.stream()
                .filter(r -> r.score100() != null)
                .mapToDouble(RawResult::score100)
                .average()
                .orElse(0.0);
    }

    /**
     * IoU 평균 — Strict PASS인 run만 집계.
     *
     * <p>FAIL 케이스는 mid가 범위 밖이라 대부분 IoU=0이다. 이를 포함하면 평균이
     * Strict PASS rate에 종속되어 "추천 범위 품질"이라는 본래 의미가 희석된다.
     * 따라서 "맞췄을 때 추천 범위가 얼마나 정답 범위와 겹치는가"만 측정한다.
     * Strict PASS가 없으면 0.0 반환.</p>
     */
    private static double meanIouOnStrictPass(List<RawResult> results) {
        return results.stream()
                .filter(r -> r.strictPass() != null && r.strictPass() == 1.0)
                .filter(r -> r.iou() != null)
                .mapToDouble(RawResult::iou)
                .average()
                .orElse(0.0);
    }

    private static Map<String, ModelReport.BucketStats> aggregateByCategory(
            Map<String, List<RawResult>> resultsByCase,
            Map<String, GoldenCase> caseById) {
        // 카테고리 라벨 → 케이스 리스트
        Map<String, List<String>> caseIdsByCategory = new LinkedHashMap<>();
        for (GoldenCase gc : caseById.values()) {
            String key = gc.category() == null ? "(none)" : gc.category();
            caseIdsByCategory.computeIfAbsent(key, k -> new ArrayList<>()).add(gc.id());
        }
        return toBucketStats(caseIdsByCategory, resultsByCase);
    }

    private static Map<String, ModelReport.BucketStats> aggregateByTag(
            Map<String, List<RawResult>> resultsByCase,
            Map<String, GoldenCase> caseById) {
        // 태그 → 케이스 리스트 (한 케이스가 여러 태그에 포함 가능)
        Map<String, List<String>> caseIdsByTag = new LinkedHashMap<>();
        for (GoldenCase gc : caseById.values()) {
            for (String tag : gc.tags()) {
                caseIdsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(gc.id());
            }
        }
        return toBucketStats(caseIdsByTag, resultsByCase);
    }

    private static Map<String, ModelReport.BucketStats> toBucketStats(
            Map<String, List<String>> caseIdsByBucket,
            Map<String, List<RawResult>> resultsByCase) {
        Map<String, ModelReport.BucketStats> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : caseIdsByBucket.entrySet()) {
            String label = e.getKey();
            List<RawResult> bucketRuns = new ArrayList<>();
            Set<String> caseSet = new java.util.HashSet<>(e.getValue());
            for (String caseId : caseSet) {
                List<RawResult> cr = resultsByCase.get(caseId);
                if (cr != null) {
                    bucketRuns.addAll(cr);
                }
            }
            if (bucketRuns.isEmpty()) {
                continue;
            }
            int runs = bucketRuns.size();
            int strict = countStrict(bucketRuns);
            double strictRate = (double) strict / runs;
            double meanScore = meanScore(bucketRuns);
            double iou = meanIouOnStrictPass(bucketRuns);
            out.put(label, new ModelReport.BucketStats(
                    label, caseSet.size(), runs, strictRate, meanScore, iou));
        }
        return out;
    }

    private static List<ModelReport.CaseStats> buildCaseStats(
            Map<String, List<RawResult>> resultsByCase,
            Map<String, GoldenCase> caseById) {
        List<ModelReport.CaseStats> out = new ArrayList<>();
        for (Map.Entry<String, List<RawResult>> e : resultsByCase.entrySet()) {
            String caseId = e.getKey();
            List<RawResult> runs = e.getValue();
            GoldenCase gc = caseById.get(caseId);
            String category = gc != null && gc.category() != null ? gc.category() : "(none)";
            int total = runs.size();
            int strict = countStrict(runs);
            double strictRate = (double) strict / total;
            double meanScore = meanScore(runs);
            double iou = meanIouOnStrictPass(runs);
            out.add(new ModelReport.CaseStats(caseId, category, total, strictRate, meanScore, iou));
        }
        return out;
    }

    /** bottom 3: meanScore100 오름차순, 동률이면 strictPassRate 오름차순. 전체가 3 미만이면 전부 반환. */
    private static List<ModelReport.CaseStats> pickBottom3(List<ModelReport.CaseStats> all) {
        return all.stream()
                .sorted(Comparator
                        .comparingDouble(ModelReport.CaseStats::meanScore100)
                        .thenComparingDouble(ModelReport.CaseStats::strictPassRate))
                .limit(3)
                .toList();
    }

    private static List<ModelReport.ExceptionEntry> buildExceptionList(List<RawResult> results) {
        return results.stream()
                .filter(r -> r.exceptionType() != null)
                .map(r -> new ModelReport.ExceptionEntry(
                        r.caseId(), r.runIdx(), r.exceptionType(), r.exceptionMessage()))
                .toList();
    }

    // 사용되지 않지만 향후 필요 시 활용 — IDE의 unused 경고를 피하기 위해 private으로 제한
    @SuppressWarnings("unused")
    private static Map<String, Integer> countByModel(List<RawResult> results) {
        Map<String, Integer> map = new HashMap<>();
        for (RawResult r : results) {
            map.merge(r.model(), 1, Integer::sum);
        }
        return map;
    }
}
