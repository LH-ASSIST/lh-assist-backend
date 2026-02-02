package com.lh.assist.approval.api.dto.response;

import com.lh.assist.document.domain.enums.ApprovalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "문서 승인 상세 응답")
public class DocumentApprovalResponse {
    @Schema(description = "문서 ID", example = "123")
    private final Long docId;
    @Schema(description = "승인 상태", example = "APPROVED")
    private final ApprovalStatus approvalStatus;
    @Schema(description = "검토자 이름", example = "박차장")
    private final String reviewerName;
    @Schema(description = "검토자 직급", example = "차장")
    private final String reviewerTitle;
    @Schema(description = "검토자 부서", example = "규정준수팀")
    private final String reviewerDept;
    @Schema(description = "검토 일시", example = "2024-01-06T15:30:00")
    private final LocalDateTime reviewedAt;
    @Schema(description = "검토 의견", example = "하자담보책임 조항을 주택법 제46조에 맞게 수정 완료 확인했습니다.")
    private final String reviewComment;
}