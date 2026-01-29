package com.lh.assist.notice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(
        @NotBlank
        @Size(max = 200)
        String title,
        String content
) {
}