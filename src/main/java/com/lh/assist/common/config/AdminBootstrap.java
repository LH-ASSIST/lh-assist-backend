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

    private static final UserDepartment DEFAULT_DEPARTMENT = UserDepartment.ETC;
    private static final UserPosition DEFAULT_POSITION = UserPosition.ETC;

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
                .department(DEFAULT_DEPARTMENT)
                .position(DEFAULT_POSITION)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build();
        userRepository.save(admin);
        log.info("관리자 계정이 생성되었습니다. email={}", email);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}