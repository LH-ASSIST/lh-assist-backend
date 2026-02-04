package com.lh.assist.audit.application;

import com.lh.assist.audit.domain.entity.AuditLog;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.audit.domain.repository.AuditLogRepository;
import com.lh.assist.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String DEFAULT_REFERENCE = "N/A";

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog log(
            AuditActionType actionType,
            AuditTargetType targetType,
            Long targetId,
            String referenceKey,
            User actor
    ) {
        String resolvedReference = (referenceKey == null || referenceKey.isBlank())
                ? DEFAULT_REFERENCE
                : referenceKey;
        AuditLog auditLog = AuditLog.builder()
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .s3Key(resolvedReference)
                .actor(actor)
                .build();
        return auditLogRepository.save(auditLog);
    }
}