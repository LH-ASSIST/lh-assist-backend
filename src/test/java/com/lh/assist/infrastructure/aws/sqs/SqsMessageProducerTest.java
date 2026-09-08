package com.lh.assist.infrastructure.aws.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.support.ReflectionTestUtils;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * SqsMessageProducer가 실제로 만드는 SQS 페이로드를 검증한다
 *
 * 자소서의 "분석 요청에도 기준일을 포함해... 검색되도록 했다"는 주장의 실제
 * 배선(baseDate/validItemIds/validManualItemIds가 SQS 메시지에 실제로 담기는지)을
 * 지금까지 아무 테스트도 검증한 적이 없었다
 *
 * sendAnalysisRequested에는 10% 확률로 인위적 장애를 주입하는 카오스 테스트 코드가
 * 있어(SqsMessageProducer 상단 주석 참고) 실패할 수 있으므로, 성공할 때까지 재시도한다
 * (5회 재시도 시 전부 실패할 확률은 0.1^5 = 0.001% 수준)
 */
class SqsMessageProducerTest {

	private final AmazonSQS amazonSqs = mock(AmazonSQS.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private SqsMessageProducer newProducer() {
		SqsMessageProducer producer = new SqsMessageProducer(
				amazonSqs, objectMapper, auditLogService, userRepository);
		ReflectionTestUtils.setField(producer, "queueUrl", "https://sqs.test/queue");
		ReflectionTestUtils.setField(producer, "maxAttempts", 3);
		ReflectionTestUtils.setField(producer, "retryDelayMs", 0L);
		return producer;
	}

	private SendMessageRequest sendOnceEventuallySucceeding(SqsMessageProducer producer) {
		when(amazonSqs.sendMessage(any(SendMessageRequest.class)))
				.thenReturn(new SendMessageResult());

		SystemException lastFailure = null;
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				producer.sendAnalysisRequested(
						100L, 10L, 20L, "documents/20/doc.pdf",
						LocalDate.of(2023, 5, 1),
						List.of(1L, 2L, 3L),
						List.of(9L)
				);
				ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
				verify(amazonSqs, times(1)).sendMessage(captor.capture());
				return captor.getValue();
			} catch (SystemException chaosInjected) {
				lastFailure = chaosInjected;
			}
		}
		throw new AssertionError("5회 재시도에도 카오스 주입을 피하지 못함", lastFailure);
	}

	@Test
	@DisplayName("baseDate와 validItemIds/validManualItemIds가 SQS 페이로드에 camelCase/snake_case로 모두 담긴다")
	void 분석요청_페이로드에_기준일과_유효조항ID가_담긴다() throws Exception {
		SqsMessageProducer producer = newProducer();
		SendMessageRequest request = sendOnceEventuallySucceeding(producer);

		assertThat(request.getQueueUrl()).isEqualTo("https://sqs.test/queue");

		JsonNode payload = objectMapper.readTree(request.getMessageBody());

		assertThat(payload.get("baseDate").asText()).isEqualTo("2023-05-01");
		assertThat(payload.get("base_date").asText()).isEqualTo("2023-05-01");

		assertThat(toLongList(payload.get("validItemIds"))).containsExactly(1L, 2L, 3L);
		assertThat(toLongList(payload.get("valid_item_ids"))).containsExactly(1L, 2L, 3L);

		assertThat(toLongList(payload.get("validManualItemIds"))).containsExactly(9L);
		assertThat(toLongList(payload.get("valid_manual_item_ids"))).containsExactly(9L);

		assertThat(payload.get("jobId").asLong()).isEqualTo(100L);
		assertThat(payload.get("docId").asLong()).isEqualTo(20L);
		assertThat(payload.get("s3Key").asText()).isEqualTo("documents/20/doc.pdf");
	}

	@Test
	@DisplayName("같은 문서라도 기준일이 다르면 페이로드의 기준일과 validItemIds가 달라진다")
	void 같은_문서_다른_기준일이면_페이로드도_달라진다() throws Exception {
		SqsMessageProducer producer = newProducer();
		when(amazonSqs.sendMessage(any(SendMessageRequest.class))).thenReturn(new SendMessageResult());

		ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);

		retrySend(producer, LocalDate.of(2023, 1, 1), List.of(1L));
		retrySend(producer, LocalDate.of(2024, 6, 1), List.of(1L, 2L));

		verify(amazonSqs, times(2)).sendMessage(captor.capture());
		List<SendMessageRequest> requests = captor.getAllValues();

		JsonNode first = objectMapper.readTree(requests.get(0).getMessageBody());
		JsonNode second = objectMapper.readTree(requests.get(1).getMessageBody());

		assertThat(first.get("baseDate").asText()).isEqualTo("2023-01-01");
		assertThat(second.get("baseDate").asText()).isEqualTo("2024-06-01");
		assertThat(toLongList(first.get("validItemIds"))).containsExactly(1L);
		assertThat(toLongList(second.get("validItemIds"))).containsExactly(1L, 2L);
	}

	private void retrySend(SqsMessageProducer producer, LocalDate baseDate, List<Long> validItemIds) {
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				producer.sendAnalysisRequested(1L, 1L, 1L, "doc.pdf", baseDate, validItemIds, List.of());
				return;
			} catch (SystemException chaosInjected) {
				// 카오스 주입, 재시도
			}
		}
		throw new AssertionError("5회 재시도에도 카오스 주입을 피하지 못함");
	}

	private List<Long> toLongList(JsonNode arrayNode) {
		return objectMapper.convertValue(arrayNode, objectMapper.getTypeFactory()
				.constructCollectionType(List.class, Long.class));
	}
}
