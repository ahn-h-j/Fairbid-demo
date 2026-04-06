package com.cos.fairbid.admin.application.service;

import com.cos.fairbid.admin.application.dto.DailyAuctionStatsResult;
import com.cos.fairbid.admin.application.dto.StatsOverviewResult;
import com.cos.fairbid.admin.application.dto.TimePatternResult;
import com.cos.fairbid.admin.application.port.in.GetStatsUseCase;
import com.cos.fairbid.admin.application.port.out.LoadStatsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 통계 서비스
 * 관리자 대시보드용 통계 데이터를 집계한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService implements GetStatsUseCase {

    private final LoadStatsPort loadStatsPort;

    @Override
    public StatsOverviewResult getOverview(Integer days) {
        LocalDateTime from = calculateFromDate(days);

        long totalAuctions = loadStatsPort.countTotalAuctions(from);
        long completedAuctions = loadStatsPort.countCompletedAuctions(from);
        double avgBidCount = loadStatsPort.getAvgBidCount(from);
        double avgPriceIncrease = loadStatsPort.getAvgPriceIncreaseRate(from);
        long extendedAuctions = loadStatsPort.countExtendedAuctions(from);

        // 낙찰률 계산
        double completedRate = totalAuctions > 0
                ? (double) completedAuctions / totalAuctions * 100
                : 0;

        // 연장 발생률 계산
        double extensionRate = totalAuctions > 0
                ? (double) extendedAuctions / totalAuctions * 100
                : 0;

        return new StatsOverviewResult(
                totalAuctions,
                Math.round(completedRate * 10) / 10.0,
                Math.round(avgBidCount * 10) / 10.0,
                Math.round(avgPriceIncrease * 10) / 10.0,
                Math.round(extensionRate * 10) / 10.0
        );
    }

    @Override
    public DailyAuctionStatsResult getDailyStats(Integer days) {
        LocalDateTime from = calculateFromDate(days);

        // 일별 데이터 조회
        List<LoadStatsPort.DailyCount> newAuctions = loadStatsPort.getDailyNewAuctions(from);
        List<LoadStatsPort.DailyCount> completedAuctions = loadStatsPort.getDailyCompletedAuctions(from);
        List<LoadStatsPort.DailyCount> bids = loadStatsPort.getDailyBids(from);

        // Map으로 변환 (중복 키 발생 시 합산)
        Map<LocalDate, Long> newMap = newAuctions.stream()
                .collect(Collectors.toMap(LoadStatsPort.DailyCount::date, LoadStatsPort.DailyCount::count, Long::sum));
        Map<LocalDate, Long> completedMap = completedAuctions.stream()
                .collect(Collectors.toMap(LoadStatsPort.DailyCount::date, LoadStatsPort.DailyCount::count, Long::sum));
        Map<LocalDate, Long> bidMap = bids.stream()
                .collect(Collectors.toMap(LoadStatsPort.DailyCount::date, LoadStatsPort.DailyCount::count, Long::sum));

        // 모든 날짜 수집 및 정렬
        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(newMap.keySet());
        allDates.addAll(completedMap.keySet());
        allDates.addAll(bidMap.keySet());

        // 결과 생성
        List<DailyAuctionStatsResult.DailyStat> dailyStats = allDates.stream()
                .map(date -> new DailyAuctionStatsResult.DailyStat(
                        date,
                        newMap.getOrDefault(date, 0L),
                        completedMap.getOrDefault(date, 0L),
                        bidMap.getOrDefault(date, 0L)
                ))
                .toList();

        return new DailyAuctionStatsResult(dailyStats);
    }

    @Override
    public TimePatternResult getTimePattern(Integer days) {
        LocalDateTime from = calculateFromDate(days);

        List<LoadStatsPort.HourlyBidCount> hourlyBids = loadStatsPort.getHourlyBidCounts(from);

        // 0~23시 모든 시간대 포함하도록 보정 (중복 시간대 발생 시 합산)
        Map<Integer, Long> hourMap = hourlyBids.stream()
                .collect(Collectors.toMap(LoadStatsPort.HourlyBidCount::hour, LoadStatsPort.HourlyBidCount::count, Long::sum));

        List<TimePatternResult.HourlyBidCount> result = new ArrayList<>();
        int peakHour = 0;
        long peakCount = 0;

        for (int hour = 0; hour < 24; hour++) {
            long count = hourMap.getOrDefault(hour, 0L);
            result.add(new TimePatternResult.HourlyBidCount(hour, count));
            if (count > peakCount) {
                peakCount = count;
                peakHour = hour;
            }
        }

        return new TimePatternResult(result, peakHour, peakCount);
    }

    /**
     * 기간에 따른 시작 날짜를 계산한다.
     *
     * @param days 일 수 (null이면 전체)
     * @return 시작 날짜 (null이면 전체 조회)
     */
    private LocalDateTime calculateFromDate(Integer days) {
        if (days == null || days <= 0) {
            return null;
        }
        return LocalDateTime.now().minusDays(days);
    }
}
