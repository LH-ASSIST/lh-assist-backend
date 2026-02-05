package com.lh.assist.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.analysis.api.dto.request.AnalysisCallbackRequest;
import com.lh.assist.analysis.api.dto.request.AnalysisEvidencePayload;
import com.lh.assist.analysis.api.dto.request.AnalysisRiskItemPayload;
import com.lh.assist.analysis.api.dto.request.AnalysisSectionPayload;
import com.lh.assist.analysis.domain.entity.AnalysisEvidence;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.entity.AnalysisRiskItem;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import com.lh.assist.analysis.domain.enums.AnalysisJobStatus;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisEvidenceRepository;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.repository.AnalysisRiskItemRepository;
import com.lh.assist.analysis.domain.repository.AnalysisSectionRepository;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.common.exception.AnalysisException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.AnalysisStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisCallbackService {

	private final AnalysisJobRepository analysisJobRepository;
	private final AnalysisSectionRepository analysisSectionRepository;
	private final AnalysisRiskItemRepository analysisRiskItemRepository;
	private final AnalysisEvidenceRepository analysisEvidenceRepository;
	private final AuditLogService auditLogService;
	private final ObjectMapper objectMapper;

	/**
	 * FastAPI 분석 완료 콜백을 처리한다
	 *
	 * 요청된 상태에 따라 분석 작업/결과/문서 상태를 동기화한다
	 *
	 * @param jobId 분석 작업 ID
	 * @param request 콜백 요청 바디
	 */
	@Transactional
	public void handleCallback(
			Long jobId,
			AnalysisCallbackRequest request
	) {
        AnalysisJob job = analysisJobRepository.findById(jobId)
                .orElseThrow(() -> new AnalysisException(ErrorCode.ANALYSIS_NOT_FOUND));

        AnalysisResult result = job.getAnalysisResult();
        if (result == null) {
            throw new AnalysisException(ErrorCode.ANALYSIS_NOT_FOUND);
        }
        AnalysisResultStatus status = request.resolveStatus();
        if (status == null) {
            throw new AnalysisException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Integer resolvedScore = request.resolveTotalRiskScore() != null
                ? request.resolveTotalRiskScore()
                : result.getTotalRiskScore();

        result.updateStatus(status, resolvedScore);
        String parsedJsonS3Key = request.resolveParsedJsonS3Key();
        if (parsedJsonS3Key != null) {
            result.updateParsedJsonS3Key(parsedJsonS3Key);
        }
        job.updateStatus(mapToJobStatus(status), request.getFailReason());

        Document document = job.getDocument();
        document.updateAnalysisStatus(mapToDocumentStatus(status));

        if (status == AnalysisResultStatus.SUCCEEDED && request.getSections() != null) {
            persistSections(result, request.getSections());
        }

        if (status == AnalysisResultStatus.SUCCEEDED || status == AnalysisResultStatus.FAILED) {
            AuditActionType actionType = status == AnalysisResultStatus.SUCCEEDED
                    ? AuditActionType.ANALYSIS_CALLBACK_SUCCEEDED
                    : AuditActionType.ANALYSIS_CALLBACK_FAILED;
            auditLogService.log(
                    actionType,
                    AuditTargetType.ANALYSIS_JOB,
                    job.getJobId(),
                    document.getS3Key(),
                    job.getRequestedBy()
            );
        }
    }

    private void persistSections(
            AnalysisResult result,
            List<AnalysisSectionPayload> sections
    ) {
        if (sections.isEmpty()) {
            return;
        }

        analysisEvidenceRepository.deleteAllByAnalysisSection_AnalysisResult_AnalysisId(result.getAnalysisId());
        analysisRiskItemRepository.deleteAllByAnalysisSection_AnalysisResult_AnalysisId(result.getAnalysisId());
        analysisSectionRepository.deleteAllByAnalysisResult_AnalysisId(result.getAnalysisId());

        for (AnalysisSectionPayload sectionPayload : sections) {
            AnalysisSection section = analysisSectionRepository.save(AnalysisSection.builder()
                    .analysisResult(result)
                    .externalSectionId(sectionPayload.getExternalSectionId())
                    .bbox(serializeBbox(sectionPayload.getBbox()))
                    .pageNumber(sectionPayload.getPageNumber())
                    .isViolation(Boolean.TRUE.equals(sectionPayload.getIsViolation()))
                    .riskScore(sectionPayload.getRiskScore())
                    .reasoning(sectionPayload.getReasoning())
                    .build());

            List<AnalysisRiskItemPayload> riskItems = sectionPayload.getRiskItems();
            if (riskItems == null || riskItems.isEmpty()) {
                continue;
            }

            for (AnalysisRiskItemPayload riskItemPayload : riskItems) {
                AnalysisRiskItem riskItem = analysisRiskItemRepository.save(AnalysisRiskItem.builder()
                        .analysisSection(section)
                        .riskType(riskItemPayload.getRiskType())
                        .detectedText(riskItemPayload.getDetectedText())
                        .guideMessage(riskItemPayload.getGuideMessage())
                        .priority(riskItemPayload.getPriority())
                        .similarCaseContent(riskItemPayload.getSimilarCaseContent())
                        .reasoning(riskItemPayload.getReasoning())
                        .build());

                List<AnalysisEvidencePayload> evidences = riskItemPayload.getEvidences();
                if (evidences == null || evidences.isEmpty()) {
                    continue;
                }

                for (AnalysisEvidencePayload evidencePayload : evidences) {
                    analysisEvidenceRepository.save(AnalysisEvidence.builder()
                            .analysisSection(section)
                            .analysisRiskItem(riskItem)
                            .sourceType(evidencePayload.getSourceType())
                            .sourceId(evidencePayload.getSourceId())
                            .quote(evidencePayload.getQuote())
                            .build());
                }
            }
        }
    }

    private String serializeBbox(List<Double> bbox) {
        if (bbox == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(bbox);
        } catch (JsonProcessingException e) {
            throw new AnalysisException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private AnalysisJobStatus mapToJobStatus(AnalysisResultStatus status) {
        return switch (status) {
            case REQUESTED -> AnalysisJobStatus.REQUESTED;
            case RUNNING -> AnalysisJobStatus.RUNNING;
            case SUCCEEDED -> AnalysisJobStatus.SUCCEEDED;
            case FAILED -> AnalysisJobStatus.FAILED;
        };
    }

    private AnalysisStatus mapToDocumentStatus(AnalysisResultStatus status) {
        return switch (status) {
            case REQUESTED, RUNNING -> AnalysisStatus.ANALYZING;
            case SUCCEEDED -> AnalysisStatus.COMPLETED;
            case FAILED -> AnalysisStatus.FAILED;
        };
    }
}
