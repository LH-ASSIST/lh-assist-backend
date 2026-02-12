package com.lh.assist.common.mail;

import com.lh.assist.user.application.event.TempPasswordIssuedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@Slf4j
@RequiredArgsConstructor
public class TempPasswordMailListener {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.sender:}")
    private String sender;

	@Async("mailTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleTempPasswordIssued(TempPasswordIssuedEvent event) {
		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setTo(event.email());
			if (sender != null && !sender.isBlank()) {
				helper.setFrom(sender);
			}
			helper.setSubject("[LH Assist] 임시 비밀번호 안내");
			helper.setText(EmailTemplateBuilder.buildTempPasswordEmail(event.tempPassword()), true);
			mailSender.send(message);
		} catch (MessagingException e) {
			log.error("임시 비밀번호 메일 생성 실패: email={}", event.email(), e);
		} catch (Exception e) {
			log.error("임시 비밀번호 메일 발송 실패: email={}", event.email(), e);
		}
	}
}