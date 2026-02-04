package com.lh.assist.approval.api;

import com.lh.assist.approval.api.docs.DocumentApprovalAssignDocs;
import com.lh.assist.approval.api.docs.DocumentApprovalGetDocs;
import com.lh.assist.approval.api.docs.DocumentApprovalReviewDocs;
import com.lh.assist.approval.api.dto.request.ApprovalAssignRequest;
import com.lh.assist.approval.api.dto.request.ApprovalReviewRequest;
import com.lh.assist.approval.api.dto.response.DocumentApprovalResponse;
import com.lh.assist.approval.application.DocumentApprovalService;
import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.document.api.docs.DocumentApiDocs;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
@DocumentApiDocs
@Validated
public class DocumentApprovalController {

    private final DocumentApprovalService approvalService;

    @GetMapping("/{docId}/approval")
    @PreAuthorize("isAuthenticated()")
    @DocumentApprovalGetDocs
    public ResponseEntity<ApiResponse<DocumentApprovalResponse>> getApproval(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long docId
    ) {
        DocumentApprovalResponse response = approvalService.getApproval(docId, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{docId}/approval/assign")
    @PreAuthorize("isAuthenticated()")
    @DocumentApprovalAssignDocs
    public ResponseEntity<ApiResponse<DocumentApprovalResponse>> assignApprover(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long docId,
            @Valid @RequestBody ApprovalAssignRequest request
    ) {
        DocumentApprovalResponse response = approvalService.assignApprover(
                docId,
                request.approverId(),
                principal
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{docId}/approval/review")
    @PreAuthorize("isAuthenticated()")
    @DocumentApprovalReviewDocs
    public ResponseEntity<ApiResponse<DocumentApprovalResponse>> review(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long docId,
            @Valid @RequestBody ApprovalReviewRequest request
    ) {
        DocumentApprovalResponse response = approvalService.review(
                docId,
                request.status(),
                request.reviewComment(),
                principal
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
