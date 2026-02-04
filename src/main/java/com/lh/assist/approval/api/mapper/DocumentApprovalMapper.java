package com.lh.assist.approval.api.mapper;

import com.lh.assist.approval.api.dto.response.DocumentApprovalResponse;
import com.lh.assist.approval.domain.entity.DocumentApproval;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import com.lh.assist.user.api.dto.response.UserListResponse;
import java.util.List;

public class DocumentApprovalMapper {

    private DocumentApprovalMapper() {
    }

    public static DocumentApprovalResponse toResponse(
            Document document,
            DocumentApproval approval
    ) {
        return toResponse(document, approval, null);
    }

    public static DocumentApprovalResponse toResponse(
            Document document,
            DocumentApproval approval,
            List<UserListResponse> approverCandidates
    ) {
        ApprovalStatus status = document.getApprovalStatus();
        return DocumentApprovalResponse.builder()
                .docId(document.getDocId())
                .approvalStatus(status)
                .reviewerName(approval != null ? approval.getReviewerName() : null)
                .reviewerTitle(approval != null ? approval.getReviewerTitle() : null)
                .reviewerDept(approval != null ? approval.getReviewerDept() : null)
                .reviewedAt(approval != null ? approval.getReviewedAt() : null)
                .reviewComment(approval != null ? approval.getReviewComment() : null)
                .approverCandidates(approverCandidates)
                .build();
    }
}