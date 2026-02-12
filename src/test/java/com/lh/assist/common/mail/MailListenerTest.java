package com.lh.assist.common.mail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.auth.application.event.EmailVerificationIssuedEvent;
import com.lh.assist.support.ReflectionTestUtils;
import com.lh.assist.user.application.event.TempPasswordIssuedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class MailListenerTest {

	@Mock
	private JavaMailSender mailSender;

	@InjectMocks
	private EmailVerificationMailListener emailVerificationMailListener;

	@InjectMocks
	private TempPasswordMailListener tempPasswordMailListener;

	@Test
	@DisplayName("인증 메일 전송 실패가 발생해도 예외를 전파하지 않는다")
	void 인증메일_전송_실패_예외_미전파() {
		MimeMessage message = new MimeMessage((Session) null);
		when(mailSender.createMimeMessage()).thenReturn(message);
		doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(MimeMessage.class));
		ReflectionTestUtils.setField(emailVerificationMailListener, "sender", "noreply@lh.com");

		assertThatCode(() -> emailVerificationMailListener.handleEmailVerificationIssued(
				new EmailVerificationIssuedEvent("test@lh.com", "123456")
		)).doesNotThrowAnyException();

		verify(mailSender).send(any(MimeMessage.class));
	}

	@Test
	@DisplayName("임시 비밀번호 메일 전송 실패가 발생해도 예외를 전파하지 않는다")
	void 임시비밀번호메일_전송_실패_예외_미전파() {
		MimeMessage message = new MimeMessage((Session) null);
		when(mailSender.createMimeMessage()).thenReturn(message);
		doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(MimeMessage.class));
		ReflectionTestUtils.setField(tempPasswordMailListener, "sender", "noreply@lh.com");

		assertThatCode(() -> tempPasswordMailListener.handleTempPasswordIssued(
				new TempPasswordIssuedEvent("test@lh.com", "TempPass12")
		)).doesNotThrowAnyException();

		verify(mailSender).send(any(MimeMessage.class));
	}
}