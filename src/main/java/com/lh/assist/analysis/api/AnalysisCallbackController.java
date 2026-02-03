package com.lh.assist.analysis.api;

import com.lh.assist.analysis.api.dto.request.AnalysisCallbackRequest;
import com.lh.assist.analysis.application.AnalysisCallbackService;
import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.model.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis")
public class AnalysisCallbackController {

    private static final String CALLBACK_TOKEN_HEADER = "X-Analysis-Callback-Token";

    private final AnalysisCallbackService analysisCallbackService;

    @Value("${app.analysis.callback-token:}")
    private String callbackToken;

    @PostMapping("/jobs/{jobId}/callback")
    public ResponseEntity<ApiResponse<Void>> handleCallback(
            @PathVariable Long jobId,
            @RequestHeader(value = CALLBACK_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody AnalysisCallbackRequest request
    ) {
        validateToken(token);
        analysisCallbackService.handleCallback(jobId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void validateToken(String token) {
        if (callbackToken == null || callbackToken.isBlank()) {
            return;
        }
        if (!callbackToken.equals(token)) {
            throw new AuthException(ErrorCode.UNAUTHORIZED);
        }
    }
}