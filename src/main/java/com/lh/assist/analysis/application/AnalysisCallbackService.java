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
import com.lh.assist.analysis.domain.enums.AnalysisEvidenceSourceType;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.AnalysisStatus;
import com.lh.assist.reg.domain.entity.AuditItem;
import com.lh.assist.reg.domain.entity.AuditManualItem;
import com.lh.assist.reg.domain.entity.RegItem;
import com.lh.assist.reg.domain.repository.AuditItemRepository;
import com.lh.assist.reg.domain.repository.AuditManualItemRepository;
import com.lh.assist.reg.domain.repository.RegItemRepository;
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
	private final RegItemRepository regItemRepository;
	private final AuditManualItemRepository auditManualItemRepository;
	private final AuditItemRepository auditItemRepository;
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
                    AnalysisEvidence.AnalysisEvidenceBuilder builder = AnalysisEvidence.builder()
                            .analysisSection(section)
                            .analysisRiskItem(riskItem)
                            .sourceType(evidencePayload.getSourceType())
                            .sourceId(evidencePayload.getSourceId())
                            .quote(evidencePayload.getQuote());

                    resolveEvidenceSource(evidencePayload, builder);

                    analysisEvidenceRepository.save(builder.build());
                }
            }
        }
    }

    /**
     * AI가 인용한 근거의 source_id를 실제 조항 엔티티로 검증/해석한다
     *
     * AI가 존재하지 않는 조항 id를 근거로 들면(할루시네이션) 저장 시점에 걸러낸다
     * REG_ITEM 근거는 판정 시점의 규정 버전/발효일을 스냅샷으로 함께 남긴다
     *
     * @param payload AI 콜백으로 전달된 근거 페이로드
     * @param builder 채워 넣을 AnalysisEvidence 빌더
     */
    private void resolveEvidenceSource(
            AnalysisEvidencePayload payload,
            AnalysisEvidence.AnalysisEvidenceBuilder builder
    ) {
        AnalysisEvidenceSourceType sourceType = payload.getSourceType();
        Long sourceId = parseSourceId(payload.getSourceId());

        switch (sourceType) {
            case REG_ITEM -> {
                RegItem regItem = regItemRepository.findById(sourceId)
                        .orElseThrow(() -> new AnalysisException(ErrorCode.ANALYSIS_EVIDENCE_SOURCE_NOT_FOUND));
                builder.regItem(regItem)
                        .regVersionSnapshot(regItem.getRegulation().getVersion())
                        .regEffectiveDateSnapshot(regItem.getRegulation().getEffectiveDate());
            }
            case AUDIT_MANUAL_ITEM -> {
                AuditManualItem auditManualItem = auditManualItemRepository.findById(sourceId)
                        .orElseThrow(() -> new AnalysisException(ErrorCode.ANALYSIS_EVIDENCE_SOURCE_NOT_FOUND));
                builder.auditManualItem(auditManualItem);
            }
            case AUDIT_ITEM -> {
                AuditItem auditItem = auditItemRepository.findById(sourceId)
                        .orElseThrow(() -> new AnalysisException(ErrorCode.ANALYSIS_EVIDENCE_SOURCE_NOT_FOUND));
                builder.auditItem(auditItem);
            }
        }
    }

    private Long parseSourceId(String sourceId) {
        try {
            return Long.parseLong(sourceId);
        } catch (NumberFormatException | NullPointerException ex) {
            throw new AnalysisException(ErrorCode.ANALYSIS_EVIDENCE_SOURCE_NOT_FOUND);
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
