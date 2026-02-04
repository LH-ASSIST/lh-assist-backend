package com.lh.assist.audit.api;

import com.lh.assist.admin.api.docs.AdminApiDocs;
import com.lh.assist.audit.api.docs.AdminAuditLogSearchDocs;
import com.lh.assist.audit.api.dto.response.AuditLogResponse;
import com.lh.assist.audit.api.mapper.AuditLogMapper;
import com.lh.assist.audit.application.AuditLogQueryService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.common.model.ApiResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@AdminApiDocs
public class AdminAuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    @AdminAuditLogSearchDocs
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> search(
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String keyword,
            @ParameterObject Pageable pageable
    ) {
        Page<AuditLogResponse> responses = auditLogQueryService.search(
                        actionType,
                        targetType,
                        actorId,
                        from,
                        to,
                        keyword,
                        pageable
                )
                .map(AuditLogMapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}