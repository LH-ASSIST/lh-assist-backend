package com.lh.assist.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.analysis.domain.entity.AnalysisDashboardSummary;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisDashboardSummaryRepository;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.support.ReflectionTestUtils;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisDashboardSummaryBatchServiceTest {

    @Mock
    private AnalysisJobRepository analysisJobRepository;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    @Mock
    private AnalysisDashboardSummaryRepository analysisDashboardSummaryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnalysisDashboardSummaryBatchService batchService;

    @Test
    @DisplayName("월간 요약 배치는 유저별 요약을 저장해야 한다")
    void 월간_요약_배치_성공() {
        ReflectionTestUtils.setField(batchService, "cronZone", "Asia/Seoul");
        User user = TestDataFactory.userWithId("batch@lh.com", 1L);

        when(analysisJobRepository.findDistinctUserIdsByCreatedAtBetween(any(), any()))
                .thenReturn(List.of(1L));
        when(userRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(user));
        when(analysisJobRepository.countByRequestedBy_UserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(5L);
        when(analysisResultRepository.averageTotalRiskScoreLatestByDocumentBetween(
                eq(1L),
                eq(AnalysisResultStatus.SUCCEEDED.name()),
                any(),
                any()
        )).thenReturn(30.0);
        when(analysisResultRepository.countHighRiskLatestByDocumentBetween(
                eq(1L),
                eq(AnalysisResultStatus.SUCCEEDED.name()),
                eq(40),
                any(),
                any()
        )).thenReturn(2L);
        when(analysisResultRepository.countLowRiskLatestByDocumentBetween(
                eq(1L),
                eq(AnalysisResultStatus.SUCCEEDED.name()),
                eq(20),
                any(),
                any()
        )).thenReturn(1L);
        when(analysisDashboardSummaryRepository.findByUser_UserIdAndYearMonth(eq(1L), anyString()))
                .thenReturn(Optional.empty());

        batchService.refreshMonthlySummaries();

        ArgumentCaptor<List<AnalysisDashboardSummary>> captor = ArgumentCaptor.forClass(List.class);
        verify(analysisDashboardSummaryRepository).saveAll(captor.capture());

        AnalysisDashboardSummary summary = captor.getValue().getFirst();
        YearMonth expected = YearMonth.from(LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(summary.getUser()).isEqualTo(user);
        assertThat(summary.getYearMonth()).isEqualTo(expected.toString());
        assertThat(summary.getMonthlyReviewCount()).isEqualTo(5L);
        assertThat(summary.getHighRiskDocumentCount()).isEqualTo(2L);
        assertThat(summary.getAverageSafetyScore()).isEqualTo(70);
        assertThat(summary.getLowRiskDocumentCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이번 달 분석 유저가 없으면 저장하지 않아야 한다")
    void 월간_요약_유저_없음() {
        when(analysisJobRepository.findDistinctUserIdsByCreatedAtBetween(any(), any()))
                .thenReturn(List.of());

        batchService.refreshMonthlySummaries();

        verify(analysisDashboardSummaryRepository, never()).saveAll(any());
    }
}