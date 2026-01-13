package com.lh.assist.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LH-Assist API Document")
                        .description("LH 공공사업 리스크 탐지 AI 어시스턴트 백엔드 API 명세서")
                        .version("v1.0.0"));
    }
}