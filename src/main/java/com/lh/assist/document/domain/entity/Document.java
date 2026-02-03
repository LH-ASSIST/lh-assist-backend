package com.lh.assist.document.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.document.domain.enums.AnalysisStatus;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.enums.MetadataStatus;
import com.lh.assist.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doc_id")
    private Long docId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocumentType docType;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status")
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_status")
    private MetadataStatus metadataStatus = MetadataStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
    public Document(
            String title,
            DocumentType docType,
            String s3Key,
            LocalDate baseDate,
            User user
    ) {
        this.title = title;
        this.docType = docType;
        this.s3Key = s3Key;
        this.baseDate = baseDate;
        this.user = user;
    }

    public void updateApprovalStatus(ApprovalStatus approvalStatus) {
        if (approvalStatus == null) {
            throw new IllegalArgumentException("승인 상태는 null일 수 없습니다.");
        }
        this.approvalStatus = approvalStatus;
    }

    public void updateAnalysisStatus(AnalysisStatus analysisStatus) {
        if (analysisStatus == null) {
            throw new IllegalArgumentException("분석 상태는 null일 수 없습니다.");
        }
        this.analysisStatus = analysisStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document that)) return false;
        return docId != null && docId.equals(that.docId);
    }

    @Override
    public int hashCode() {
        return docId != null ? docId.hashCode() : getClass().hashCode();
    }
}