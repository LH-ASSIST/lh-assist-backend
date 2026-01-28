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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.admin.bootstrap.cli", name = "enabled", havingValue = "true")
public class AdminBootstrapCliRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;

    @Value("${app.admin.bootstrap.email:}")
    private String email;

    @Value("${app.admin.bootstrap.password:}")
    private String password;

    @Value("${app.admin.bootstrap.name:}")
    private String name;

    @Value("${app.admin.bootstrap.test-user.enabled:false}")
    private boolean testUserEnabled;

    @Value("${app.admin.bootstrap.test-user.email:}")
    private String testUserEmail;

    @Value("${app.admin.bootstrap.test-user.password:}")
    private String testUserPassword;

    @Value("${app.admin.bootstrap.test-user.name:}")
    private String testUserName;

    private static final UserDepartment DEFAULT_ADMIN_DEPARTMENT = UserDepartment.ETC;
    private static final UserPosition DEFAULT_ADMIN_POSITION = UserPosition.ETC;
    private static final UserDepartment DEFAULT_USER_DEPARTMENT = UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE;
    private static final UserPosition DEFAULT_USER_POSITION = UserPosition.STAFF;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (isBlank(email) || isBlank(password) || isBlank(name)) {
            log.warn("관리자 부트스트랩 설정이 누락되었습니다. 관리자 계정을 생성하지 않습니다.");
            exit(1);
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.info("관리자 계정이 이미 존재합니다. email={}", email);
        } else {
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
        }

        createTestUserIfNeeded();
        exit(0);
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
            log.info("테스트 계정이 이미 존재합니다. email={}", testUserEmail);
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

    private void exit(int code) {
        int exitCode = org.springframework.boot.SpringApplication.exit(applicationContext, () -> code);
        System.exit(exitCode);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}