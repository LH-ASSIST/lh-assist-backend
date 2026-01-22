package com.lh.assist.analysis.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.regulation.domain.enums.domain.entity.Document;
import com.lh.assist.regulation.domain.enums.domain.repository.DocumentRepository;
import com.lh.assist.infrastructure.aws.sqs.SqsMessageProducer;
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

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SqsMessageProducer sqsMessageProducer;

    @InjectMocks
    private AnalysisService analysisService;

    @Test
    @DisplayName("문서 소유자가 아니면 접근 거부가 발생해야 한다")
    void 문서_소유자_아니면_거부() {
        User requester = user(1L);
        User owner = user(2L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/2/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build();
        when(userRepository.findByEmail("req@lh.com")).thenReturn(Optional.of(requester));
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

        LocalDate baseDate = LocalDate.now();

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(10L, "req@lh.com", baseDate))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(sqsMessageProducer, never()).sendAnalysisRequested(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("기준일이 없으면 입력값 오류가 발생해야 한다")
    void 기준일_없으면_입력값_오류() {
        User requester = user(1L);
        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(null)
                .user(requester)
                .build();
        when(userRepository.findByEmail("req@lh.com")).thenReturn(Optional.of(requester));
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(11L, "req@lh.com", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("문서 ID가 없으면 입력값 오류가 발생해야 한다")
    void 문서_ID_없으면_입력값_오류() {
        User requester = user(1L);
        when(userRepository.findByEmail("req@lh.com")).thenReturn(Optional.of(requester));

        LocalDate baseDate = LocalDate.now();

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(null, "req@lh.com", baseDate))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("사용자가 없으면 인증 오류가 발생해야 한다")
    void 사용자_없으면_인증_오류() {
        when(userRepository.findByEmail("missing@lh.com")).thenReturn(Optional.empty());

        LocalDate baseDate = LocalDate.now();

        assertThatThrownBy(() -> analysisService.requestAnalysisByEmail(1L, "missing@lh.com", baseDate))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
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
