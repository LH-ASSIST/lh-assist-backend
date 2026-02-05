package com.lh.assist.admin.application;

import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SuggestionException;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * QnA 게시판 전체 목록을 조회한다
     *
     * 관리자 전용으로 모든 건의사항을 확인할 수 있다
     *
     * @param pageable 페이징할 단위
     * @return 조회된 건의 엔티티
     */
    @Transactional(readOnly = true)
    public Page<Suggestion> getAllSuggestions(Pageable pageable) {
        return suggestionRepository.findAll(pageable);
    }

    /**
     * QnA 건의사항에 답변을 등록한다
     *
     * @param suggestionId 답변할 건의 ID
     * @param answerContent 답변 내용
     * @return 답변이 등록된 건의 엔티티
     */
    @Transactional
    public Suggestion answerSuggestion(
            Long suggestionId,
            String answerContent,
            Long actorId
    ) {
        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new SuggestionException(ErrorCode.SUGGESTION_NOT_FOUND));
        suggestion.answer(answerContent);
        User actor = getUserById(actorId);
        auditLogService.log(
                AuditActionType.SUGGESTION_ANSWERED,
                AuditTargetType.SUGGESTION,
                suggestion.getSuggestionId(),
                "suggestionId:" + suggestion.getSuggestionId(),
                actor
        );
        return suggestion;
    }

    private User getUserById(Long userId) {
        if (userId == null) {
            throw new SuggestionException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new SuggestionException(ErrorCode.USER_NOT_FOUND));
    }
}