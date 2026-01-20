package com.lh.assist.analysis.application;

import com.lh.assist.analysis.api.dto.AnalysisRequestResponse;
import com.lh.assist.analysis.domain.AnalysisJob;
import com.lh.assist.analysis.domain.AnalysisJobRepository;
import com.lh.assist.analysis.domain.AnalysisJobStatus;
import com.lh.assist.analysis.domain.AnalysisResult;
import com.lh.assist.analysis.domain.AnalysisResultRepository;
import com.lh.assist.analysis.domain.AnalysisResultStatus;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.infrastructure.aws.sqs.SqsMessageProducer;
import com.lh.assist.document.domain.Document;
import com.lh.assist.document.domain.DocumentRepository;
import com.lh.assist.user.domain.User;
import com.lh.assist.user.domain.UserRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AnalysisService {

	private final AnalysisResultRepository analysisResultRepository;
	private final AnalysisJobRepository analysisJobRepository;
	private final DocumentRepository documentRepository;
	private final UserRepository userRepository;
	private final SqsMessageProducer sqsMessageProducer;

	@Transactional
	public AnalysisRequestResponse requestAnalysisByEmail(Long docId, String email, LocalDate baseDate) {
		User user = getUserByEmail(email);
		Document document = getDocumentById(docId);
		if (!document.getUser().equals(user)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}

		LocalDate resolvedBaseDate = baseDate != null ? baseDate : document.getBaseDate();
		if (resolvedBaseDate == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		AnalysisResult analysisResult = analysisResultRepository.save(AnalysisResult.builder()
				.document(document)
				.baseDate(resolvedBaseDate)
				.status(AnalysisResultStatus.REQUESTED)
				.build());

		AnalysisJob analysisJob = analysisJobRepository.save(AnalysisJob.builder()
				.analysisResult(analysisResult)
				.document(document)
				.baseDate(resolvedBaseDate)
				.status(AnalysisJobStatus.REQUESTED)
				.retryCount(0)
				.requestedBy(user)
				.build());

		AnalysisRequestResponse response = AnalysisRequestResponse.builder()
				.analysisId(analysisResult.getAnalysisId())
				.jobId(analysisJob.getJobId())
				.analysisStatus(analysisResult.getStatus())
				.jobStatus(analysisJob.getStatus())
				.baseDate(resolvedBaseDate)
				.createdAt(analysisJob.getCreatedAt())
				.build();

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				sqsMessageProducer.sendAnalysisRequested(analysisJob.getJobId(), user.getUserId());
			}
		});

		return response;
	}

	private User getUserByEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private Document getDocumentById(Long docId) {
		if (docId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return documentRepository.findById(docId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
	}
}