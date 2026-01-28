package com.lh.assist.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.admin.bootstrap.cli", name = "enabled", havingValue = "true")
public class AdminBootstrapCliRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;
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

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(email) || isBlank(password) || isBlank(name)) {
            log.warn("관리자 부트스트랩 설정이 누락되었습니다. 관리자 계정을 생성하지 않습니다.");
            exit(1);
            return;
        }
        adminBootstrapService.createAccountsIfNeeded(
                email,
                password,
                name,
                testUserEnabled,
                testUserEmail,
                testUserPassword,
                testUserName
        );
        exit(0);
    }

    private void exit(int code) {
        int exitCode = org.springframework.boot.SpringApplication.exit(applicationContext, () -> code);
        System.exit(exitCode);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
