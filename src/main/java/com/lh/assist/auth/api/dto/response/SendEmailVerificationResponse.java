package com.lh.assist.auth.api.dto.response;

import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendEmailVerificationResponse {
	private final String email;
	private final EmailVerificationPurpose purpose;
	private final LocalDateTime expiresAt;
}