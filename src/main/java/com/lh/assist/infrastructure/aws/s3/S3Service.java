package com.lh.assist.infrastructure.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.lh.assist.common.exception.DocumentException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

	private final AmazonS3 amazonS3;

	@Value("${spring.cloud.aws.s3.bucket}")
	private String bucket;

	/**
	 * 파일을 S3에 업로드하고 저장된 키를 반환한다
	 *
	 * @param file 업로드 파일
	 * @param keyPrefix S3 키 접두사
	 * @return 저장된 S3 키
	 */
	public String uploadFile(
			MultipartFile file,
			String keyPrefix
	) {
		if (file == null || file.isEmpty()) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}

		String key = buildKey(keyPrefix, file.getOriginalFilename());
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(file.getSize());
		metadata.setContentType(file.getContentType());

		try (InputStream inputStream = file.getInputStream()) {
			amazonS3.putObject(
					bucket,
					key,
					inputStream,
					metadata
			);
			return key;
		} catch (IOException | RuntimeException ex) {
			throw new SystemException(ErrorCode.S3_UPLOAD_FAILED, ex);
		}
    }

	/**
	 * S3 객체를 삭제한다
	 *
	 * @param key 삭제할 S3 키
	 */
	public void deleteFile(String key) {
		if (key == null || key.isBlank()) {
			return;
		}

		try {
			amazonS3.deleteObject(bucket, key);
		} catch (RuntimeException ex) {
			throw new SystemException(ErrorCode.S3_DELETE_FAILED, ex);
		}
	}

	/**
	 * S3 원본 문서에 대한 presigned URL을 발급한다
	 *
	 * 만료 시간은 호출자가 지정하며 최소 1초 이상이어야 한다
	 *
	 * @param key S3 객체 키
	 * @param expiresIn URL 만료 시간
	 * @return 발급된 presigned URL
	 */
	public URL generatePresignedUrl(
			String key,
			Duration expiresIn
	) {
		if (key == null || key.isBlank()) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (expiresIn == null || expiresIn.isNegative() || expiresIn.isZero()) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}
		Instant expiresAt = Instant.now().plus(expiresIn);
		GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
				.withMethod(HttpMethod.GET)
				.withExpiration(Date.from(expiresAt));
		try {
			return amazonS3.generatePresignedUrl(request);
		} catch (RuntimeException ex) {
			throw new SystemException(ErrorCode.INTERNAL_SERVER_ERROR, ex);
		}
	}

	/**
	 * 키 접두사와 파일명을 조합해 S3 키를 생성한다
	 *
	 * @param keyPrefix S3 키 접두사
	 * @param originalFilename 원본 파일명
	 * @return 생성된 S3 키
	 */
	private String buildKey(
			String keyPrefix,
			String originalFilename
	) {
		String safePrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "" : keyPrefix.trim();
		String filename = (originalFilename == null || originalFilename.isBlank())
				? "file"
				: originalFilename.trim();
		String uuid = UUID.randomUUID().toString();
		if (safePrefix.isEmpty()) {
			return uuid + "-" + filename;
		}
		return safePrefix + "/" + uuid + "-" + filename;
	}
}
