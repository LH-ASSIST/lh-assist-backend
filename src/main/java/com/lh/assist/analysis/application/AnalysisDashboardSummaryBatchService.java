package com.lh.assist.analysis.application;

import com.lh.assist.analysis.domain.entity.AnalysisDashboardSummary;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisDashboardSummaryRepository;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisDashboardSummaryBatchService {

    // safetyScore = 100 - totalRiskScore
    // high risk: safetyScore <= 60 -> totalRiskScore >= 40
    // low risk: safetyScore >= 80 -> totalRiskScore <= 20
    private static final int HIGH_RISK_TOTAL_SCORE_THRESHOLD_INCLUSIVE = 40;
    private static final int LOW_RISK_TOTAL_SCORE_THRESHOLD_INCLUSIVE = 20;

    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisDashboardSummaryRepository analysisDashboardSummaryRepository;
    private final UserRepository userRepository;
    @Value("${app.analysis.dashboard.cron-zone:Asia/Seoul}")
    private String cronZone;

    @Scheduled(
            cron = "${app.analysis.dashboard.cron:0 0 0 * * *}",
            zone = "${app.analysis.dashboard.cron-zone:Asia/Seoul}"
    )
    @Transactional
    public void refreshMonthlySummaries() {
        String resolvedZone = (cronZone == null || cronZone.isBlank()) ? "Asia/Seoul" : cronZone;
        ZoneId zoneId = ZoneId.of(resolvedZone);
        LocalDate today = LocalDate.now(zoneId);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        List<Long> userIds = analysisJobRepository
                .findDistinctUserIdsByCreatedAtBetween(startOfMonth, startOfNextMonth);
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        String yearMonth = currentMonth.toString();
        List<AnalysisDashboardSummary> summaries = new ArrayList<>(userIds.size());

        for (Long userId : userIds) {
            User user = userMap.get(userId);
            if (user == null) {
                continue;
            }

            long monthlyReviewCount = analysisJobRepository
                    .countByRequestedBy_UserIdAndCreatedAtBetween(userId, startOfMonth, startOfNextMonth);

        Double avgRiskScore = analysisResultRepository
                .averageTotalRiskScoreLatestByDocumentBetween(
                        userId,
                        AnalysisResultStatus.SUCCEEDED.name(),
                        startOfMonth,
                        startOfNextMonth
                );
        int averageSafetyScore = resolveAverageSafetyScore(avgRiskScore);

        long highRiskCount = analysisResultRepository.countHighRiskLatestByDocumentBetween(
                userId,
                AnalysisResultStatus.SUCCEEDED.name(),
                HIGH_RISK_TOTAL_SCORE_THRESHOLD_INCLUSIVE,
                startOfMonth,
                startOfNextMonth
        );

        long lowRiskCount = analysisResultRepository.countLowRiskLatestByDocumentBetween(
                userId,
                AnalysisResultStatus.SUCCEEDED.name(),
                LOW_RISK_TOTAL_SCORE_THRESHOLD_INCLUSIVE,
                startOfMonth,
                startOfNextMonth
        );

            AnalysisDashboardSummary summary = analysisDashboardSummaryRepository
                    .findByUser_UserIdAndYearMonth(userId, yearMonth)
                    .orElseGet(() -> AnalysisDashboardSummary.builder()
                            .user(user)
                            .yearMonth(yearMonth)
                            .build());

            summary.updateStats(
                    monthlyReviewCount,
                    highRiskCount,
                    averageSafetyScore,
                    lowRiskCount
            );
            summaries.add(summary);
        }

        analysisDashboardSummaryRepository.saveAll(summaries);
    }

    private int resolveAverageSafetyScore(Double avgRiskScore) {
        if (avgRiskScore == null) {
            return 0;
        }
        int safetyScore = (int) Math.round(100.0 - avgRiskScore);
        if (safetyScore < 0) {
            return 0;
        }
        if (safetyScore > 100) {
            return 100;
        }
        return safetyScore;
    }
}