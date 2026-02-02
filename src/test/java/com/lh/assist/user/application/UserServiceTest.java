package com.lh.assist.user.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.UserException;
import com.lh.assist.user.api.dto.request.UserPasswordChangeRequest;
import com.lh.assist.user.api.dto.request.UserPasswordResetRequest;
import com.lh.assist.user.application.event.TempPasswordIssuedEvent;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("마이페이지 조회 시 사용자 없으면 NOT_FOUND가 발생해야 한다")
    void 마이페이지_사용자_없음() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyPage(99L))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 변경 시 현재 비밀번호가 틀리면 예외가 발생해야 한다")
    void 비밀번호_변경_현재_비밀번호_불일치() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        UserPasswordChangeRequest request = new UserPasswordChangeRequest("wrong", "New1234!");

        assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_INVALID);
    }

    @Test
    @DisplayName("비밀번호 변경이 성공하면 암호화된 비밀번호로 변경되어야 한다")
    void 비밀번호_변경_성공() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current123!", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("New1234!")).thenReturn("newEncoded");

        UserPasswordChangeRequest request = new UserPasswordChangeRequest("Current123!", "New1234!");

        userService.changePassword(1L, request);

        verify(passwordEncoder).encode("New1234!");
    }

    @Test
    @DisplayName("비밀번호 찾기 시 사용자가 없으면 NOT_FOUND가 발생해야 한다")
    void 비밀번호_찾기_사용자_없음() {
        when(userRepository.findByEmail("missing@lh.com")).thenReturn(Optional.empty());

        UserPasswordResetRequest request = new UserPasswordResetRequest("missing@lh.com");

        assertThatThrownBy(() -> userService.resetPassword(request))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 찾기 시 임시 비밀번호가 저장되고 메일이 발송되어야 한다")
    void 비밀번호_찾기_성공() {
        User user = user();
        when(userRepository.findByEmail("tester@lh.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("tempEncoded");

        UserPasswordResetRequest request = new UserPasswordResetRequest("tester@lh.com");

        userService.resetPassword(request);

        verify(passwordEncoder).encode(org.mockito.ArgumentMatchers.anyString());
        ArgumentCaptor<TempPasswordIssuedEvent> captor = ArgumentCaptor.forClass(TempPasswordIssuedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().email()).isEqualTo("tester@lh.com");
    }

    @Test
    @DisplayName("일반 사용자는 요청 부서와 무관하게 본인 부서만 조회되어야 한다")
    void 일반_사용자_부서_강제() {
        User requester = TestDataFactory.userWith(
                "requester@lh.com",
                "encoded",
                "Requester",
                UserRole.USER,
                UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE,
                UserPosition.STAFF,
                UserStatus.ACTIVE,
                true
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findAllByDepartmentAndStatus(UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE, UserStatus.ACTIVE))
                .thenReturn(List.of(requester));

        List<User> users = userService.getUsersByDepartment(1L);

        org.assertj.core.api.Assertions.assertThat(users).hasSize(1);
        verify(userRepository).findAllByDepartmentAndStatus(UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE, UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("관리자는 전체 사용자 조회가 가능해야 한다")
    void 관리자_전체_조회() {
        User admin = admin();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(user(), admin));

        List<User> users = userService.getAllUsersForAdmin(1L, null);

        org.assertj.core.api.Assertions.assertThat(users).hasSize(2);
        verify(userRepository).findAll();
    }

    private static User user() {
        return TestDataFactory.userWith(
                "tester@lh.com",
                "encoded",
                "Tester",
                UserRole.USER,
                UserDepartment.ETC,
                UserPosition.ETC,
                UserStatus.ACTIVE,
                true
        );
    }

    private static User admin() {
        return TestDataFactory.userWith(
                "admin@lh.com",
                "encoded",
                "Admin",
                UserRole.ADMIN,
                UserDepartment.PUBLIC_HOUSING_HEADQUARTERS,
                UserPosition.CHIEF,
                UserStatus.ACTIVE,
                true
        );
    }
}
