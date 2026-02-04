package com.lh.assist.analysis.application;

import com.lh.assist.analysis.api.dto.request.AnalysisCallbackRequest;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisJobStatus;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisCallbackService {

	private final AnalysisJobRepository analysisJobRepository;
	private final AuditLogService auditLogService;

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
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));

        AnalysisResult result = job.getAnalysisResult();
        if (result == null) {
            throw new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND);
        }
        AnalysisResultStatus status = request.getStatus();
        Integer resolvedScore = request.getTotalRiskScore() != null
                ? request.getTotalRiskScore()
                : result.getTotalRiskScore();

        result.updateStatus(status, resolvedScore);
        job.updateStatus(mapToJobStatus(status), request.getFailReason());

        Document document = job.getDocument();
        document.updateAnalysisStatus(mapToDocumentStatus(status));

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