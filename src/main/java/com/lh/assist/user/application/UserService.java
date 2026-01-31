package com.lh.assist.user.application;

import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.UserException;
import com.lh.assist.user.api.dto.request.UserPasswordChangeRequest;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
}
