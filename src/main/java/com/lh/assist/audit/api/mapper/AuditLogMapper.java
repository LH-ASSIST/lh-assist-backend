package com.lh.assist.audit.api.mapper;

import com.lh.assist.audit.api.dto.response.AuditLogResponse;
import com.lh.assist.audit.domain.entity.AuditLog;
import com.lh.assist.user.domain.entity.User;

public final class AuditLogMapper {

    private AuditLogMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog auditLog) {
        User actor = auditLog.getActor();
        return AuditLogResponse.builder()
                .logId(auditLog.getLogId())
                .actionType(auditLog.getActionType())
                .actionDescription(auditLog.getActionType().getDescription())
                .targetType(auditLog.getTargetType())
                .targetDescription(auditLog.getTargetType().getDescription())
                .targetId(auditLog.getTargetId())
                .referenceKey(auditLog.getS3Key())
                .actorId(actor.getUserId())
                .actorEmail(actor.getEmail())
                .actorName(actor.getName())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}