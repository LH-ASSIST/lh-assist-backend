package com.lh.assist.analysis.api.mapper;

import com.lh.assist.analysis.api.dto.response.AnalysisRequestResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisRiskItemResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisSectionResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisSummaryResponse;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.entity.AnalysisRiskItem;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import java.time.LocalDate;
import java.util.List;

public final class AnalysisMapper {

    private AnalysisMapper() {
    }

    public static AnalysisRequestResponse toRequestResponse(
            AnalysisResult analysisResult,
            AnalysisJob analysisJob,
            LocalDate baseDate
    ) {
        return AnalysisRequestResponse.builder()
                .analysisId(analysisResult.getAnalysisId())
                .jobId(analysisJob.getJobId())
                .analysisStatus(analysisResult.getStatus())
                .jobStatus(analysisJob.getStatus())
                .baseDate(baseDate)
                .createdAt(analysisJob.getCreatedAt())
                .build();
    }

    public static AnalysisSummaryResponse toSummaryResponse(
            AnalysisResult result,
            int totalRiskScore,
            String riskLevel,
            int totalViolations
    ) {
        return AnalysisSummaryResponse.builder()
                .analysisId(result.getAnalysisId())
                .totalRiskScore(totalRiskScore)
                .riskLevel(riskLevel)
                .totalViolations(totalViolations)
                .build();
    }

    public static AnalysisSectionResponse toSectionResponse(
            AnalysisSection section,
            List<AnalysisRiskItem> riskItems
    ) {
        List<AnalysisRiskItemResponse> riskItemResponses = riskItems.stream()
                .map(AnalysisMapper::toRiskItemResponse)
                .toList();

        return AnalysisSectionResponse.builder()
                .sectionId(section.getSectionId())
                .page(section.getPageNumber())
                .bbox(section.getBbox())
                .isViolation(section.isViolation())
                .riskScore(section.getRiskScore())
                .reasoning(section.getReasoning())
                .riskItems(riskItemResponses)
                .build();
    }

    public static AnalysisRiskItemResponse toRiskItemResponse(AnalysisRiskItem item) {
        return AnalysisRiskItemResponse.builder()
                .riskId(item.getRiskId())
                .riskType(item.getRiskType())
                .detectedText(item.getDetectedText())
                .guideMessage(item.getGuideMessage())
                .priority(item.getPriority())
                .similarCaseContent(item.getSimilarCaseContent())
                .reasoning(item.getReasoning())
                .build();
    }
}