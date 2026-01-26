package com.lh.assist.suggestion.application;

import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SuggestionException;
import com.lh.assist.suggestion.api.dto.request.SuggestionCreateRequest;
import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final SuggestionViewCountService viewCountService;
    private final UserRepository userRepository;

    /**
     * QnA게시판을 상세 조회하고 조회수를 1 증가시킨다
     *
     * 예시) 일반 사용자 A가 suggestionId=10을 조회하면
     * - 해당 글이 공개글이면 그대로 조회된다
     * - 비공개글이면 작성자(A) 또는 관리자만 조회할 수 있고,
     *   그 외 사용자는 ACCESS_DENIED가 발생한다
     *
     * @param userId 요청 사용자 ID
     * @param isAdmin 관리자 여부
     * @param suggestionId 조회할 건의 ID
     * @return 조회된 건의 엔티티
     */
    @Transactional(readOnly = true)
    public Suggestion getSuggestion(
            Long userId,
            boolean isAdmin,
            Long suggestionId
    ) {
        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new SuggestionException(ErrorCode.SUGGESTION_NOT_FOUND));
        validateViewPermission(userId, isAdmin, suggestion);
        viewCountService.increment(suggestionId);
        return suggestion;
    }

    /***
     * QnA 게시판 목록을 조회한다
     *
     * 일반 사용가자는 공개된 목록만을,
     * 관리자는 전체 QnA를 확인할 수 있다
     *
     * @param userId 요청 사용자 ID
     * @param isAdmin 관리자 여부
     * @param pageable 페이징할 단위
     * @return 조회된 건의 엔티티
     */
    @Transactional(readOnly = true)
    public Page<Suggestion> getSuggestions(
            Long userId,
            boolean isAdmin,
            Pageable pageable
    ) {
        if (isAdmin) {
            return suggestionRepository.findAll(pageable);
        }
        return suggestionRepository.findVisibleByUserId(userId, pageable);
    }

    /**
     * QnA 건의사항을 등록한다
     *
     * @param userId 요청 사용자 ID
     * @param request 등록할 건의사항 정보
     * @return 생성된 건의 엔티티
     */
    @Transactional
    public Suggestion createSuggestion(
            Long userId,
            SuggestionCreateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new SuggestionException(ErrorCode.UNAUTHORIZED));
        Suggestion suggestion = Suggestion.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .isPrivate(request.isPrivate())
                .isAnonymous(request.isAnonymous())
                .user(user)
                .build();
        return suggestionRepository.save(suggestion);
    }

    /**
     * 비공개 글 조회 권한을 확인한다
     *
     * 공개글이면 허용하고, 비공개글은 작성자 또는 관리자만 허용한다
     *
     * @param userId 요청 사용자 ID
     * @param isAdmin 관리자 여부
     * @param suggestion 조회 대상 건의
     */
    private void validateViewPermission(
            Long userId,
            boolean isAdmin,
            Suggestion suggestion
    ) {
        if (!suggestion.isPrivate()) {
            return;
        }
        if (isAdmin) {
            return;
        }
        if (suggestion.getUser() == null || !suggestion.getUser().getUserId().equals(userId)) {
            throw new SuggestionException(ErrorCode.ACCESS_DENIED);
        }
    }
}
