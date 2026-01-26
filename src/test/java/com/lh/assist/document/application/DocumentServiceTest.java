package com.lh.assist.document.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.common.exception.DocumentException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.regulation.domain.enums.domain.entity.Document;
import com.lh.assist.regulation.domain.enums.domain.repository.DocumentRepository;
import com.lh.assist.regulation.domain.enums.domain.enums.DocumentType;
import com.lh.assist.infrastructure.aws.s3.S3Service;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private DocumentService documentService;

    @Test
    @DisplayName("지원하지 않는 확장자는 업로드 시 예외가 발생해야 한다")
    void 지원하지_않는_확장자_업로드_실패() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.txt",
                "text/plain",
                "data".getBytes()
        );

        assertThatThrownBy(() -> documentService.uploadDocument(1L, DocumentType.NOTICE, null, file))
                .isInstanceOf(DocumentException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DOCUMENT_NOT_SUPPORTED);

        verify(s3Service, never()).uploadFile(any(), any());
    }

    @Test
    @DisplayName("DB 저장 실패 시 S3 보상 삭제가 호출되어야 한다")
    void 저장_실패시_S3_보상_삭제() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "data".getBytes()
        );
        User user = user(1L);
        LocalDate baseDate = LocalDate.now();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(s3Service.uploadFile(file, "documents/1")).thenReturn("documents/1/key.pdf");
        when(documentRepository.save(any(Document.class))).thenThrow(new RuntimeException("db error"));

        assertThatThrownBy(() -> documentService.uploadDocument(1L, DocumentType.NOTICE, baseDate, file))
                .isInstanceOf(SystemException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        verify(s3Service).deleteFile("documents/1/key.pdf");
    }

    @Test
    @DisplayName("다른 사용자의 문서를 조회하면 접근 거부가 발생해야 한다")
    void 다른_사용자_문서_조회_거부() {
        User owner = user(1L);
        User requester = user(2L);
        Document document = Document.builder()
                .title("문서")
                .docType(DocumentType.NOTICE)
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build();
        when(userRepository.findByEmail("other@lh.com")).thenReturn(Optional.of(requester));
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.getDocumentByEmail("other@lh.com", 10L))
                .isInstanceOf(DocumentException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("S3 삭제 실패 시 문서 삭제가 예외를 전달해야 한다")
    void S3_삭제_실패시_예외_전달() {
        User owner = user(1L);
        Document document = Document.builder()
                .title("문서")
                .docType(DocumentType.NOTICE)
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build();
        when(userRepository.findByEmail("owner@lh.com")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(20L)).thenReturn(Optional.of(document));
        SystemException failure = new SystemException(ErrorCode.S3_DELETE_FAILED);
        doThrow(failure).when(s3Service).deleteFile("documents/1/key.pdf");

        assertThatThrownBy(() -> documentService.deleteDocumentByEmail("owner@lh.com", 20L))
                .isInstanceOf(SystemException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.S3_DELETE_FAILED);

        verify(documentRepository).delete(document);
    }

    private static User user(Long userId) {
        return User.builder()
                .userId(userId)
                .email("user" + userId + "@lh.com")
                .password("hashed")
                .name("Tester")
                .department(UserDepartment.PUBLIC_HOUSING_HEADQUARTERS)
                .position(UserPosition.DEPUTY_MANAGER)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build();
    }
}
