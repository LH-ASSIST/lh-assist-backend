package com.lh.assist.analysis.application;

import com.lh.assist.analysis.api.dto.response.AnalysisRequestResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisSectionResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisSummaryResponse;
import com.lh.assist.analysis.api.dto.response.AnalysisRiskItemResponse;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import com.lh.assist.analysis.domain.entity.AnalysisRiskItem;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.repository.AnalysisSectionRepository;
import com.lh.assist.analysis.domain.repository.AnalysisRiskItemRepository;
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
import java.util.List;
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
	private final AnalysisSectionRepository analysisSectionRepository;
	private final AnalysisRiskItemRepository analysisRiskItemRepository;
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
		validateOwnership(user, document);

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
	 * 최신 분석 결과 요약 정보를 조회한다
	 *
	 * 분석 결과가 없으면 예외를 발생시킨다
	 *
	 * @param docId 문서 ID
	 * @param email 사용자 이메일
	 * @return 분석 요약 응답
	 */
	@Transactional(readOnly = true)
	public AnalysisSummaryResponse getAnalysisSummary(
			Long docId,
			String email
	) {
		User user = getUserByEmail(email);
		Document document = getDocumentById(docId);
		validateOwnership(user, document);

		AnalysisResult result = getLatestSucceeded(docId);
		int totalRiskScore = result.getTotalRiskScore() != null ? result.getTotalRiskScore() : 0;
		String level = resolveRiskLevel(totalRiskScore);
		int totalViolations = (int) analysisSectionRepository
				.countByAnalysisResult_AnalysisIdAndIsViolationTrue(result.getAnalysisId());

		return AnalysisSummaryResponse.builder()
				.analysisId(result.getAnalysisId())
				.totalRiskScore(totalRiskScore)
				.riskLevel(level)
				.totalViolations(totalViolations)
				.build();
	}

	/**
	 * 섹션별 상세 분석 결과를 조회한다
	 *
	 * 분석 결과가 없으면 예외를 발생시킨다
	 *
	 * @param docId 문서 ID
	 * @param email 사용자 이메일
	 * @return 섹션별 분석 결과 목록
	 */
	@Transactional(readOnly = true)
	public List<AnalysisSectionResponse> getAnalysisSections(
			Long docId,
			String email
	) {
		User user = getUserByEmail(email);
		Document document = getDocumentById(docId);
		validateOwnership(user, document);

		AnalysisResult result = getLatestSucceeded(docId);
		List<AnalysisSection> sections = analysisSectionRepository
				.findAllByAnalysisResult_AnalysisId(result.getAnalysisId());
		List<Long> sectionIds = sections.stream()
				.map(AnalysisSection::getSectionId)
				.toList();
		List<AnalysisRiskItem> riskItems = sectionIds.isEmpty()
				? List.of()
				: analysisRiskItemRepository.findAllByAnalysisSection_SectionIdIn(sectionIds);
		var riskItemMap = riskItems.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						item -> item.getAnalysisSection().getSectionId()
				));

		return sections.stream()
				.map(section -> AnalysisSectionResponse.builder()
						.sectionId(section.getSectionId())
						.page(section.getPageNumber())
						.bbox(section.getBbox())
						.isViolation(section.isViolation())
						.riskScore(section.getRiskScore())
						.reasoning(section.getReasoning())
						.riskItems(riskItemMap.getOrDefault(section.getSectionId(), List.of())
								.stream()
								.map(item -> AnalysisRiskItemResponse.builder()
										.riskId(item.getRiskId())
										.riskType(item.getRiskType())
										.detectedText(item.getDetectedText())
										.guideMessage(item.getGuideMessage())
										.priority(item.getPriority())
										.similarCaseContent(item.getSimilarCaseContent())
										.reasoning(item.getReasoning())
										.build())
								.toList())
						.build())
				.toList();
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

	/**
	 * 문서 소유자 여부를 검증한다
	 *
	 * 소유자가 아니면 예외를 발생시킨다
	 *
	 * @param user 요청 사용자
	 * @param document 대상 문서
	 */
	private void validateOwnership(
			User user,
			Document document
	) {
		if (!document.getUser().equals(user)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
	}

	/**
	 * 최신 성공 분석 결과를 조회한다
	 *
	 * 결과가 없으면 예외를 발생시킨다
	 *
	 * @param docId 문서 ID
	 * @return 최신 성공 분석 결과
	 */
	private AnalysisResult getLatestSucceeded(Long docId) {
		return analysisResultRepository.findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(
				docId,
				AnalysisResultStatus.SUCCEEDED
		).orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));
	}

	/**
	 * 총 리스크 점수 기준으로 위험 등급을 계산한다
	 *
	 * @param totalRiskScore 총 리스크 점수
	 * @return 위험 등급 문자열
	 */
	private String resolveRiskLevel(int totalRiskScore) {
		if (totalRiskScore >= 70) {
			return "HIGH";
		}
		if (totalRiskScore >= 30) {
			return "MEDIUM";
		}
		return "LOW";
	}
}
