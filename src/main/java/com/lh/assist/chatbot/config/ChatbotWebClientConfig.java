package com.lh.assist.chatbot.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class ChatbotWebClientConfig {

    @Bean
    public WebClient chatbotWebClient(
            WebClient.Builder builder,
            @Value("${app.chatbot.fastapi.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.chatbot.fastapi.response-timeout-ms:600000}") long responseTimeoutMs
    ) {
        long timeoutMs = Math.max(responseTimeoutMs, 1000L);
        int timeoutSeconds = (int) Math.max(timeoutMs / 1000L, 1L);

        HttpClient httpClient = HttpClient.create()
                .compress(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));

        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
