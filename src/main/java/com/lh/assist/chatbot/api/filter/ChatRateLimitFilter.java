package com.lh.assist.chatbot.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.model.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class ChatRateLimitFilter extends OncePerRequestFilter {

    private static final int MINUTE_LIMIT = 10;
    private static final int HOUR_LIMIT = 100;
    private static final DateTimeFormatter MINUTE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter HOUR_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/chat/stream");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        if (!allowRequest(ip)) {
            ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;
            response.setStatus(errorCode.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> body = ApiResponse.error(errorCode.getMessage(), errorCode.getStatus().value());
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allowRequest(String ip) {
        LocalDateTime now = LocalDateTime.now();
        String minuteKey = "rate:chat:ip:" + ip + ":m:" + now.format(MINUTE_KEY_FORMAT);
        String hourKey = "rate:chat:ip:" + ip + ":h:" + now.format(HOUR_KEY_FORMAT);

        Long minuteCount = stringRedisTemplate.opsForValue().increment(minuteKey);
        if (minuteCount != null && minuteCount == 1L) {
            stringRedisTemplate.expire(minuteKey, java.time.Duration.ofMinutes(2));
        }
        if (minuteCount != null && minuteCount > MINUTE_LIMIT) {
            return false;
        }

        Long hourCount = stringRedisTemplate.opsForValue().increment(hourKey);
        if (hourCount != null && hourCount == 1L) {
            stringRedisTemplate.expire(hourKey, java.time.Duration.ofHours(2));
        }
        return hourCount == null || hourCount <= HOUR_LIMIT;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}