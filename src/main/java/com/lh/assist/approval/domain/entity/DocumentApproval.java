package com.lh.assist.approval.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "document_approvals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentApproval extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_id", nullable = false)
    private Document document;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    @Column(name = "reviewer_title", length = 50)
    private String reviewerTitle;

    @Column(name = "reviewer_dept", length = 100)
    private String reviewerDept;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApprovalStatus status;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Builder
    public DocumentApproval(
            Document document,
            Long approverId,
            String reviewerName,
            String reviewerTitle,
            String reviewerDept,
            ApprovalStatus status,
            LocalDateTime reviewedAt,
            String reviewComment
    ) {
        this.document = document;
        this.approverId = approverId;
        this.reviewerName = reviewerName;
        this.reviewerTitle = reviewerTitle;
        this.reviewerDept = reviewerDept;
        this.status = status;
        this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment;
    }

    public void assignReviewer(
            Long approverId,
            String reviewerName,
            String reviewerTitle,
            String reviewerDept
    ) {
        this.approverId = approverId;
        this.reviewerName = reviewerName;
        this.reviewerTitle = reviewerTitle;
        this.reviewerDept = reviewerDept;
        this.status = ApprovalStatus.WAITING;
        this.reviewedAt = null;
        this.reviewComment = null;
    }

    public void markReviewed(
            ApprovalStatus status,
            LocalDateTime reviewedAt,
            String reviewComment
    ) {
        this.status = status;
        this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment;
    }
}