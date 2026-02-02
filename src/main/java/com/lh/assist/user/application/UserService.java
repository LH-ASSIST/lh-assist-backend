package com.lh.assist.user.application;

import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.UserException;
import com.lh.assist.user.api.dto.request.UserPasswordResetRequest;
import com.lh.assist.user.api.dto.request.UserPasswordChangeRequest;
import com.lh.assist.user.application.event.TempPasswordIssuedEvent;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;

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
		eventPublisher.publishEvent(new TempPasswordIssuedEvent(user.getEmail(), tempPassword));
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
	 * 일반 사용자의 부서별 사용자 목록을 조회한다
	 *
	 * 일반 사용자는 본인 부서의 ACTIVE 사용자만 조회한다
	 *
	 * @param requesterId 요청 사용자 ID
	 * @return 같은 부서의 활성 사용자 목록
	 */
	@Transactional(readOnly = true)
	public List<User> getUsersByDepartment(Long requesterId) {
		if (requesterId == null) {
			throw new UserException(ErrorCode.UNAUTHORIZED);
		}
		User requester = userRepository.findById(requesterId)
				.orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

		UserDepartment resolvedDepartment = requester.getDepartment();
		if (resolvedDepartment == null) {
			throw new UserException(ErrorCode.INVALID_INPUT_VALUE);
		}

		return userRepository.findAllByDepartmentAndStatus(resolvedDepartment, UserStatus.ACTIVE);
	}

	/**
	 * 관리자 전용 사용자 목록 조회
	 *
	 * 부서가 지정되면 해당 부서만 조회한다
	 *
	 * @param requesterId 요청 사용자 ID
	 * @param department 필터 부서
	 * @return 사용자 목록
	 */
	@Transactional(readOnly = true)
	public List<User> getAllUsersForAdmin(
			Long requesterId,
			UserDepartment department
	) {
		if (requesterId == null) {
			throw new UserException(ErrorCode.UNAUTHORIZED);
		}
		User requester = userRepository.findById(requesterId)
				.orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
		if (requester.getRole() != UserRole.ADMIN) {
			throw new UserException(ErrorCode.ACCESS_DENIED);
		}
		if (department == null) {
			return userRepository.findAll();
		}
		return userRepository.findAllByDepartment(department);
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
}
