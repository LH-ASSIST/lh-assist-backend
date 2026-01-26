package com.lh.assist.common.config;

import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${app.admin.bootstrap.email:}")
    private String email;

    @Value("${app.admin.bootstrap.password:}")
    private String password;

    @Value("${app.admin.bootstrap.name:}")
    private String name;

    private static final UserDepartment DEFAULT_ADMIN_DEPARTMENT = UserDepartment.ETC;
    private static final UserPosition DEFAULT_ADMIN_POSITION = UserPosition.ETC;
    private static final UserDepartment DEFAULT_USER_DEPARTMENT = UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE;
    private static final UserPosition DEFAULT_USER_POSITION = UserPosition.STAFF;

    @Value("${app.admin.bootstrap.test-user.enabled:false}")
    private boolean testUserEnabled;

    @Value("${app.admin.bootstrap.test-user.email:}")
    private String testUserEmail;

    @Value("${app.admin.bootstrap.test-user.password:}")
    private String testUserPassword;

    @Value("${app.admin.bootstrap.test-user.name:}")
    private String testUserName;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createAdminIfNeeded() {
        if (!enabled) {
            return;
        }
        if (isBlank(email) || isBlank(password) || isBlank(name)) {
            log.warn("관리자 부트스트랩 설정이 누락되었습니다. 관리자 계정을 생성하지 않습니다.");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .name(name)
                .department(DEFAULT_ADMIN_DEPARTMENT)
                .position(DEFAULT_ADMIN_POSITION)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build();
        userRepository.save(admin);
        log.info("관리자 계정이 생성되었습니다. email={}", email);

        createTestUserIfNeeded();
    }

    private void createTestUserIfNeeded() {
        if (!testUserEnabled) {
            return;
        }
        if (isBlank(testUserEmail) || isBlank(testUserPassword) || isBlank(testUserName)) {
            log.warn("테스트 사용자 부트스트랩 설정이 누락되었습니다. 테스트 계정을 생성하지 않습니다.");
            return;
        }
        if (userRepository.existsByEmail(testUserEmail)) {
            return;
        }

        User user = User.builder()
                .email(testUserEmail)
                .password(passwordEncoder.encode(testUserPassword))
                .name(testUserName)
                .department(DEFAULT_USER_DEPARTMENT)
                .position(DEFAULT_USER_POSITION)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build();
        userRepository.save(user);
        log.info("테스트 계정이 생성되었습니다. email={}", testUserEmail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}