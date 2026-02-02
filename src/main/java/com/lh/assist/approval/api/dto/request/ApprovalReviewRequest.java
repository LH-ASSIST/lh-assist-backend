package com.lh.assist.approval.api.dto.request;

import com.lh.assist.document.domain.enums.ApprovalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalReviewRequest(
        @NotNull
        ApprovalStatus status,
        @Size(max = 2000)
        String reviewComment
) {
}