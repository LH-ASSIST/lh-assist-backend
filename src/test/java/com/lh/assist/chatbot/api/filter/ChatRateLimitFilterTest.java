package com.lh.assist.chatbot.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class ChatRateLimitFilterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ChatRateLimitFilter filter;

    @Test
    @DisplayName("분당 제한을 초과하면 429가 반환되어야 한다")
    void 분당_제한_초과() throws ServletException, IOException {
        ValueOperations<String, String> ops = mockValueOps();
        when(ops.increment(anyString())).thenReturn(11L, 1L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().value());
    }

    @Test
    @DisplayName("제한 이내면 요청을 통과시켜야 한다")
    void 제한_이내_통과() throws ServletException, IOException {
        ValueOperations<String, String> ops = mockValueOps();
        when(ops.increment(anyString())).thenReturn(1L, 1L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isNotEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().value());
    }

    @Test
    @DisplayName("시간당 제한을 초과하면 429가 반환되어야 한다")
    void 시간당_제한_초과() throws ServletException, IOException {
        ValueOperations<String, String> ops = mockValueOps();
        when(ops.increment(anyString())).thenReturn(1L, 101L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().value());
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOps() {
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        return ops;
    }
}
