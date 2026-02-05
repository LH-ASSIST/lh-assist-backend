package com.lh.assist.user.domain.repository;

import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmail(String email);
	boolean existsByEmail(String email);
	List<User> findAllByDepartment(UserDepartment department);
	List<User> findAllByDepartmentAndStatus(
			UserDepartment department,
			UserStatus status
	);
}