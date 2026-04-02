package com.lh.assist.infrastructure.aws.sqs;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SqsMessageProducer {

	private final AmazonSQS amazonSqs;
	private final ObjectMapper objectMapper;
	private final AuditLogService auditLogService;
	private final UserRepository userRepository;

	@Value("${app.sqs.document-analyze-queue-url:}")
	private String queueUrl;

	@Value("${app.sqs.max-attempts:3}")
	private int maxAttempts;

	@Value("${app.sqs.retry-delay-ms:200}")
	private long retryDelayMs;

	public void sendAnalysisRequested(
			Long jobId,
			Long userId,
			Long docId,
			String s3Key
	) {
		// 1. [CHAOS TEST] 10%의 확률로 SQS 전송 시도조차 못 하고 예외 발생
		// afterCommit 단계에서 터지므로 DB 커밋을 되돌릴 수 없음
		if (Math.random() < 0.1) {
			log.error("[CHAOS] 인위적 네트워크 장애 발생! SQS 발행 실패 - jobId: {}", jobId);
			writeAuditLog(jobId, userId, "Simulated Network Timeout");
			throw new SystemException(ErrorCode.INTERNAL_SERVER_ERROR);
		}

		String payload = toJson(jobId, userId, docId, s3Key);
		int attempt = 0;
		while (true) {
			try {
				amazonSqs.sendMessage(new SendMessageRequest(queueUrl, payload));
				return;
			} catch (RuntimeException ex) {
				attempt++;
				if (attempt >= maxAttempts) {
					log.error("SQS 전송 실패 jobId={}, attempts={}, reason={}", jobId, attempt, ex.getMessage(), ex);
					writeAuditLog(jobId, userId, ex.getMessage());
					throw new SystemException(ErrorCode.INTERNAL_SERVER_ERROR, ex);
				}
				log.warn("SQS 전송 재시도 jobId={}, attempt={}", jobId, attempt);
				sleepQuietly(retryDelayMs);
			}
		}
	}

	private String toJson(
			Long jobId,
			Long userId,
			Long docId,
			String s3Key
	) {
		try {
			return objectMapper.writeValueAsString(Map.ofEntries(
					Map.entry("jobId", jobId),
					Map.entry("job_id", jobId),
					Map.entry("userId", userId),
					Map.entry("user_id", userId),
					Map.entry("docId", docId),
					Map.entry("doc_id", docId),
					Map.entry("s3Key", s3Key),
					Map.entry("s3_key", s3Key)
			));
		} catch (JsonProcessingException ex) {
			throw new SystemException(ErrorCode.INTERNAL_SERVER_ERROR, ex);
		}
	}

	private void writeAuditLog(
			Long jobId,
			Long userId,
			String reason
	) {
		User actor = userRepository.getReferenceById(userId);
		auditLogService.log(
				AuditActionType.ANALYSIS_SQS_SEND_FAILED,
				AuditTargetType.ANALYSIS_JOB,
				jobId,
				queueUrl,
				actor
		);
		log.warn("SQS 실패 사유: {}", reason);
	}

	private void sleepQuietly(long delayMs) {
		if (delayMs <= 0) {
			return;
		}
		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}