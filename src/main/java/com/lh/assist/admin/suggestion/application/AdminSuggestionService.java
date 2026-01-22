package com.lh.assist.admin.suggestion.application;

import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSuggestionService {

    private final SuggestionRepository suggestionRepository;

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
}