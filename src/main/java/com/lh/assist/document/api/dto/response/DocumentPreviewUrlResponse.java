package com.lh.assist.document.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "문서 미리보기 URL 응답")
public class DocumentPreviewUrlResponse {
    @Schema(description = "미리보기 URL", example = "https://s3.amazonaws.com/bucket/key?...")
    private final String url;

    @Schema(description = "만료 시각(UTC)", example = "2026-02-03T12:00:00Z")
    private final Instant expiresAt;
}