package com.lh.assist.notice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeUpdateRequest(
        @NotBlank
        @Size(max = 200)
        String title,
        String content
) {
}