package com.lh.assist.common.config;

import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final UserDepartment DEFAULT_ADMIN_DEPARTMENT = UserDepartment.ETC;
    private static final UserPosition DEFAULT_ADMIN_POSITION = UserPosition.ETC;
    private static final UserDepartment DEFAULT_USER_DEPARTMENT = UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE;
    private static final UserPosition DEFAULT_USER_POSITION = UserPosition.STAFF;

    @Transactional
    public void createAccountsIfNeeded(
            String email,
            String password,
            String name,
            boolean testUserEnabled,
            String testUserEmail,
            String testUserPassword,
            String testUserName
    ) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}