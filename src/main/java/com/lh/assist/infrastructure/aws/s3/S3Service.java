package com.lh.assist.infrastructure.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

	private final AmazonS3 amazonS3;

	@Value("${spring.cloud.aws.s3.bucket}")
	private String bucket;

	public String uploadFile(MultipartFile file, String keyPrefix) {
		if (file == null || file.isEmpty()) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}

		String key = buildKey(keyPrefix, file.getOriginalFilename());
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(file.getSize());
		metadata.setContentType(file.getContentType());

		try (InputStream inputStream = file.getInputStream()) {
			amazonS3.putObject(bucket, key, inputStream, metadata);
			return key;
		} catch (IOException | RuntimeException ex) {
			throw new SystemException(ErrorCode.S3_UPLOAD_FAILED, ex);
		}
    }

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

	private String buildKey(String keyPrefix, String originalFilename) {
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