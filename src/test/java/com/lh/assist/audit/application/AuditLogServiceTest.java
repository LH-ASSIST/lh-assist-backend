package com.lh.assist.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.audit.domain.entity.AuditLog;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.audit.domain.repository.AuditLogRepository;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("referenceKey가 비어있으면 기본값으로 저장한다")
    void referenceKey_기본값_저장() {
        User actor = TestDataFactory.userWithId("actor@lh.com", 1L);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog saved = auditLogService.log(
                AuditActionType.DOCUMENT_UPLOAD,
                AuditTargetType.DOCUMENT,
                10L,
                " ",
                actor
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog captured = captor.getValue();
        assertThat(captured.getS3Key()).isEqualTo("N/A");
        assertThat(saved.getS3Key()).isEqualTo("N/A");
    }
}