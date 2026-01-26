package com.lh.assist.user.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.enums.SuggestionCategory;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.audit.domain.entity.AuditLog;
import com.lh.assist.audit.domain.repository.AuditLogRepository;
import com.lh.assist.user.domain.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserSoftDeleteIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("사용자 삭제는 status를 DELETED로 변경하고 연관 데이터는 유지해야 한다")
    void 사용자_소프트삭제_상태변경_연관데이터_유지() {
        User user = userRepository.save(TestDataFactory.user("soft@lh.com"));
        entityManager.flush();
        Suggestion suggestion = suggestionRepository.save(Suggestion.builder()
                .title("제목")
                .content("내용")
                .category(SuggestionCategory.SYSTEM_ERROR)
                .isPrivate(false)
                .isAnonymous(true)
                .user(user)
                .build());
        AuditLog auditLog = auditLogRepository.save(AuditLog.builder()
                .actionType("DELETE_USER")
                .targetType("USER")
                .targetId(user.getUserId())
                .s3Key("s3://bucket/path")
                .actor(user)
                .build());

        entityManager.flush();
        entityManager.clear();

        userRepository.deleteById(user.getUserId());
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(user.getUserId())).isEmpty();

        String status = (String) entityManager.createNativeQuery(
                        "select status from users where user_id = :id")
                .setParameter("id", user.getUserId())
                .getSingleResult();
        assertThat(status).isEqualTo("DELETED");

        assertThat(suggestionRepository.findById(suggestion.getSuggestionId())).isPresent();
        assertThat(auditLogRepository.findById(auditLog.getLogId())).isPresent();
    }
}