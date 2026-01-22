package com.lh.assist.regulation.domain.enums.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.regulation.domain.enums.domain.enums.AnalysisStatus;
import com.lh.assist.regulation.domain.enums.domain.enums.ApprovalStatus;
import com.lh.assist.regulation.domain.enums.domain.enums.DocumentType;
import com.lh.assist.regulation.domain.enums.domain.enums.MetadataStatus;
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
    private Long docId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocumentType docType;

    @Column(nullable = false, length = 500)
    private String s3Key;

    @Column(nullable = false)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Version
    @Column(nullable = false)
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

    public void assignApprover(User approver) {
        if (this.approvalStatus != ApprovalStatus.WAITING) {
            throw new IllegalStateException("승인 대기 상태에서만 승인자 지정 가능");
        }
        this.approver = approver;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document that)) return false;
        return docId != null && docId.equals(that.docId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
