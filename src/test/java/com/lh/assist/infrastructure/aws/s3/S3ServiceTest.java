package com.lh.assist.infrastructure.aws.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.lh.assist.common.exception.DocumentException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

	@Mock
	private AmazonS3 amazonS3;

	@InjectMocks
	private S3Service s3Service;

	@BeforeEach
	void setUp() {
		setField(s3Service);
	}

	@Test
	@DisplayName("파일 업로드가 성공하면 S3에 업로드되고 키를 반환해야 한다")
	void 파일_업로드_성공하면_S3_업로드_및_키_반환() {
		MockMultipartFile file = new MockMultipartFile(
			"file",
			"sample.txt",
			"text/plain",
			"sample".getBytes(StandardCharsets.UTF_8)
		);

		String key = s3Service.uploadFile(file, "documents");

		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);

		verify(amazonS3).putObject(
			any(String.class),
			keyCaptor.capture(),
			any(),
			metadataCaptor.capture()
		);

		assertThat(key).isEqualTo(keyCaptor.getValue());
		assertThat(keyCaptor.getValue()).startsWith("documents/");
		assertThat(metadataCaptor.getValue().getContentLength()).isEqualTo(file.getSize());
		assertThat(metadataCaptor.getValue().getContentType()).isEqualTo(file.getContentType());
	}

	@Test
	@DisplayName("빈 파일이면 예외가 발생해야 한다")
	void 빈_파일이면_예외_발생() {
		MockMultipartFile file = new MockMultipartFile(
			"file",
			"empty.txt",
			"text/plain",
			new byte[0]
		);

		assertThatThrownBy(() -> s3Service.uploadFile(file, "documents"))
			.isInstanceOf(DocumentException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
	}

	@Test
	@DisplayName("업로드 중 예외가 발생하면 시스템 예외를 던져야 한다")
	void 업로드_중_예외면_시스템_예외() {
		MockMultipartFile file = new MockMultipartFile(
			"file",
			"sample.txt",
			"text/plain",
			"sample".getBytes(StandardCharsets.UTF_8)
		);
		doThrow(new RuntimeException("S3 failure"))
			.when(amazonS3)
			.putObject(any(String.class), any(String.class), any(), any(ObjectMetadata.class));

		assertThatThrownBy(() -> s3Service.uploadFile(file, "documents"))
			.isInstanceOf(SystemException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.S3_UPLOAD_FAILED);
	}

	@Test
	@DisplayName("삭제 키가 비어있으면 S3 삭제를 호출하지 않아야 한다")
	void 삭제_키_비어있으면_S3_삭제_호출_안함() {
		s3Service.deleteFile(" ");

		verifyNoInteractions(amazonS3);
	}

	@Test
	@DisplayName("삭제 중 예외가 발생하면 시스템 예외를 던져야 한다")
	void 삭제_중_예외면_시스템_예외() {
		doThrow(new RuntimeException("S3 failure"))
			.when(amazonS3)
			.deleteObject("test-bucket", "documents/file.txt");

		assertThatThrownBy(() -> s3Service.deleteFile("documents/file.txt"))
			.isInstanceOf(SystemException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.S3_DELETE_FAILED);
	}

	private static void setField(Object target) {
		try {
			var field = target.getClass().getDeclaredField("bucket");
			field.setAccessible(true);
			field.set(target, (Object) "test-bucket");
		} catch (NoSuchFieldException | IllegalAccessException ex) {
			throw new IllegalStateException("Failed to set " + "bucket", ex);
		}
	}
}