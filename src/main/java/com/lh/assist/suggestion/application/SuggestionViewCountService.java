package com.lh.assist.suggestion.application;

import com.lh.assist.common.viewcount.AbstractViewCountService;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestionViewCountService extends AbstractViewCountService {

    private final SuggestionRepository suggestionRepository;

    public SuggestionViewCountService(
            StringRedisTemplate stringRedisTemplate,
            SuggestionRepository suggestionRepository
    ) {
        super(stringRedisTemplate);
        this.suggestionRepository = suggestionRepository;
    }

    @Override
    protected String viewKeyPrefix() {
        return "suggestion:view:";
    }

    @Override
    protected String viewKeySet() {
        return "suggestion:view:keys";
    }

    @Override
    protected int incrementViewCount(Long id, int delta) {
        return suggestionRepository.incrementViewCount(id, delta);
    }

    @Override
    protected String flushFailLogMessage() {
        return "조회수 반영에 실패했습니다. key={}, delta={}";
    }

    @Override
    protected String deletedLogMessage() {
        return "삭제된 건의사항으로 조회수 반영이 중단되었습니다. key={}";
    }

    @Scheduled(fixedDelayString = "${app.suggestion.view-count.flush-interval-ms:60000}")
    @Transactional
    public void flushToDatabase() {
        flushToDatabaseInternal();
    }
}
