package com.lh.assist.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LH편 AI 컴플라이언스 시스템 API")
                        .description("공공주택 사업 리스크 사전 예방 및 규정 준수 검증 API")
                        .version("v1.0.0"));
    }

    // 인증 및 마이페이지
    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder()
                .group("인증 및 마이페이지 (Auth & MyPage)")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/user/**")
                .build();
    }

    // 리스크 분석
    @Bean
    public GroupedOpenApi analysisGroup() {
        return GroupedOpenApi.builder()
                .group("리스크 분석 (Risk Analysis)")
                .pathsToMatch("/api/v1/document/**", "/api/v1/analysis/**")
                .build();
    }

    // AI 어시스턴트 및 규정
    @Bean
    public GroupedOpenApi ragGroup() {
        return GroupedOpenApi.builder()
                .group("AI 어시스턴트 및 규정 (AI & Regulation)")
                .pathsToMatch("/api/v1/chatbot/**", "/api/v1/regulation/**")
                .build();
    }

    // 공지사항 및 건의사항
    @Bean
    public GroupedOpenApi boardGroup() {
        return GroupedOpenApi.builder()
                .group("공지사항 및 건의사항 (Board)")
                .pathsToMatch("/api/v1/notice/**", "/api/v1/qna/**")
                .build();
    }

    // 시스템 관리
    @Bean
    public GroupedOpenApi adminGroup() {
        return GroupedOpenApi.builder()
                .group("시스템 관리 (Admin Only)")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }
}