package com.lh.assist.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ChatbotWebClientConfig {

    @Bean
    public WebClient chatbotWebClient(
            WebClient.Builder builder,
            @Value("${app.chatbot.fastapi.base-url:http://localhost:8000}") String baseUrl
    ) {
        return builder.baseUrl(baseUrl).build();
    }
}