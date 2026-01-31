package com.lh.assist.user.application;

import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.UserException;
import com.lh.assist.common.mail.EmailTemplateBuilder;
import com.lh.assist.user.api.dto.request.UserPasswordResetRequest;
import com.lh.assist.user.api.dto.request.UserPasswordChangeRequest;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

	@Value("${spring.mail.sender:}")
	private String sender;

	private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
	private static final int TEMP_PASSWORD_LENGTH = 10;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	/**
	 * 현재 로그인한 사용자의 마이페이지 정보를 조회한다
	 *
	 * 사용자가 존재하지 않으면 예외를 발생시킨다
	 *
	 * @param userId 로그인 사용자 ID
	 * @return 조회된 사용자 엔티티
	 */
	@Transactional(readOnly = true)
	public User getMyPage(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
	}

	/**
	 * 사용자의 이메일로 임시 비밀번호를 발송한다
	 *
	 * 해당 이메일의 사용자가 없으면 예외를 발생시킨다
	 *
	 * @param request 비밀번호 재설정 요청 정보
	 */
	@Transactional
	public void resetPassword(UserPasswordResetRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

		String tempPassword = generateTempPassword();
		user.changePassword(passwordEncoder.encode(tempPassword));
		sendTempPasswordMail(user.getEmail(), tempPassword);
	}

	/**
	 * 현재 로그인한 사용자의 비밀번호를 변경한다
	 *
	 * 현재 비밀번호가 일치하지 않거나 사용자가 없으면 예외를 발생시킨다
	 *
	 * @param userId 로그인 사용자 ID
	 * @param request 비밀번호 변경 요청 정보
	 */
	@Transactional
	public void changePassword(
			Long userId,
			UserPasswordChangeRequest request
	) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        if (user.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AuthException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

	/**
	 * 임시 비밀번호 발급용 랜덤 문자열을 생성한다
	 *
	 * @return 임시 비밀번호 문자열
	 */
	private String generateTempPassword() {
		StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
		for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
			int index = SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length());
			builder.append(TEMP_PASSWORD_CHARS.charAt(index));
		}
		return builder.toString();
	}

	/**
	 * 임시 비밀번호 안내 메일을 발송한다
	 *
	 * @param email 수신자 이메일
	 * @param tempPassword 임시 비밀번호
	 */
	private void sendTempPasswordMail(
			String email,
			String tempPassword
	) {
		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setTo(email);
			if (sender != null && !sender.isBlank()) {
				helper.setFrom(sender);
			}
			helper.setSubject("[LH Assist] 임시 비밀번호 안내");
			helper.setText(EmailTemplateBuilder.buildTempPasswordEmail(tempPassword), true);
			mailSender.send(message);
		} catch (MessagingException e) {
			throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR, e);
		}
	}
}
