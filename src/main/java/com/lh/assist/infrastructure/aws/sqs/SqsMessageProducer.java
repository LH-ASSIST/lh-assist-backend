package com.lh.assist.infrastructure.aws.sqs;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.audit.domain.AuditLog;
import com.lh.assist.audit.domain.AuditLogRepository;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.user.domain.User;
import com.lh.assist.user.domain.UserRepository;
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
	private final AuditLogRepository auditLogRepository;
	private final UserRepository userRepository;

	@Value("${app.sqs.document-analyze-queue-url:}")
	private String queueUrl;

	@Value("${app.sqs.max-attempts:3}")
	private int maxAttempts;

	@Value("${app.sqs.retry-delay-ms:200}")
	private long retryDelayMs;

	public void sendAnalysisRequested(
			Long jobId,
			Long userId
	) {
		String payload = toJson(jobId);
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

	private String toJson(Long jobId) {
		try {
			return objectMapper.writeValueAsString(Map.of("jobId", jobId));
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
		AuditLog auditLog = AuditLog.builder()
				.actionType("ANALYSIS_SQS_SEND_FAILED")
				.targetType("ANALYSIS_JOB")
				.targetId(jobId)
				.s3Key(queueUrl)
				.actor(actor)
				.build();
		auditLogRepository.save(auditLog);
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