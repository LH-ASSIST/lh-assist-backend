package com.lh.assist.analysis.application;

import com.lh.assist.analysis.api.dto.response.AnalysisRequestResponse;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.enums.AnalysisJobStatus;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.infrastructure.aws.sqs.SqsMessageProducer;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
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

	/**
	 * 문서 분석 요청을 생성하고 SQS에 작업 요청을 발행한다
	 *
	 * 요청 사용자가 문서 소유자여야 하며 기준일이 누락되면 예외를 발생시킨다
	 *
	 * @param docId 문서 ID
	 * @param email 사용자 이메일
	 * @param baseDate 기준 일자
	 * @return 분석 요청 응답 정보
	 */
	@Transactional
	public AnalysisRequestResponse requestAnalysisByEmail(
			Long docId,
			String email,
			LocalDate baseDate
	) {
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
				sqsMessageProducer.sendAnalysisRequested(
						analysisJob.getJobId(),
						user.getUserId()
				);
			}
		});

		return response;
	}

	/**
	 * 이메일로 사용자를 조회하고 존재하지 않으면 예외를 발생시킨다
	 *
	 * @param email 사용자 이메일
	 * @return 조회된 사용자 엔티티
	 */
	private User getUserByEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	/**
	 * 문서 ID로 문서를 조회하고 존재하지 않으면 예외를 발생시킨다
	 *
	 * @param docId 문서 ID
	 * @return 조회된 문서 엔티티
	 */
	private Document getDocumentById(Long docId) {
		if (docId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return documentRepository.findById(docId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
	}
}
