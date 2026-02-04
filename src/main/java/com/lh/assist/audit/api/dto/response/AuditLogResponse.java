package com.lh.assist.audit.api.dto.response;

import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
public class AuditLogResponse {
    @Schema(description = "감사 로그 고유 ID")
    private Long logId;

    @Schema(description = "수행된 행동 코드")
    private AuditActionType actionType;

    @Schema(description = "행동 설명")
    private String actionDescription;

    @Schema(description = "대상 타입 코드")
    private AuditTargetType targetType;

    @Schema(description = "대상 타입 설명")
    private String targetDescription;

    @Schema(description = "대상 엔티티 ID")
    private Long targetId;

    @Schema(description = "추가 참조 키 (예: s3Key, noticeId:123)")
    private String referenceKey;

    @Schema(description = "행동 수행자 사용자 ID")
    private Long actorId;

    @Schema(description = "행동 수행자 이메일")
    private String actorEmail;

    @Schema(description = "행동 수행자 이름")
    private String actorName;

    @Schema(description = "로그 생성 시각")
    private LocalDateTime createdAt;
}