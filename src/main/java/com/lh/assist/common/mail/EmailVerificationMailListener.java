package com.lh.assist.common.mail;

import com.lh.assist.auth.application.event.EmailVerificationIssuedEvent;
import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class EmailVerificationMailListener {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.sender:}")
    private String sender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEmailVerificationIssued(EmailVerificationIssuedEvent event) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(event.email());
            if (sender != null && !sender.isBlank()) {
                helper.setFrom(sender);
            }
            helper.setSubject("[LH Assist] 이메일 인증 코드");
            helper.setText(EmailTemplateBuilder.buildVerificationEmail(event.code()), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}