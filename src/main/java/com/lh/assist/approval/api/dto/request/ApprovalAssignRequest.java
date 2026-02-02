package com.lh.assist.approval.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApprovalAssignRequest(
        @NotNull
        Long approverId
) {
}