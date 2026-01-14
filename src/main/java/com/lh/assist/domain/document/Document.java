package com.lh.assist.domain.document;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.domain.user.User;
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
    @Column(name = "doc_type", columnDefinition = "doc_type_enum")
    private DocumentType docType;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false)
    private LocalDate baseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", columnDefinition = "analysis_status_enum")
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", columnDefinition = "approval_status_enum")
    private ApprovalStatus approvalStatus = ApprovalStatus.WAITING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Builder
    public Document(
            String title,
            DocumentType docType,
            String filePath,
            LocalDate baseDate,
            User user
    ) {
        this.title = title;
        this.docType = docType;
        this.filePath = filePath;
        this.baseDate = baseDate;
        this.user = user;
    }

    public void assignApprover(User approver) {
        this.approver = approver;
    }
}