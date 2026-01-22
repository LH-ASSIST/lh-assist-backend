package com.lh.assist.suggestion;

import com.lh.assist.suggestion.application.SuggestionViewCountService;
import com.lh.assist.suggestion.domain.Suggestion;
import com.lh.assist.suggestion.domain.SuggestionCategory;
import com.lh.assist.suggestion.domain.SuggestionRepository;
import com.lh.assist.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

class SuggestionViewCountServiceIntegrationTest extends IntegrationTestBase {

    @MockitoSpyBean
    private SuggestionRepository suggestionRepository;

    @Autowired
    private SuggestionViewCountService viewCountService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("조회수 누적값이 배치 플러시로 DB에 반영되어야 한다")
    void 조회수_누적값이_배치_플러시로_DB에_반영된다() {
        Suggestion saved = suggestionRepository.save(Suggestion.builder()
                .title("제목")
                .content("내용")
                .category(SuggestionCategory.SYSTEM_ERROR)
                .isPrivate(false)
                .isAnonymous(true)
                .user(null)
                .build());

        viewCountService.increment(saved.getSuggestionId());
        viewCountService.increment(saved.getSuggestionId());

        viewCountService.flushToDatabase();

        entityManager.clear();
        Suggestion updated = suggestionRepository.findById(saved.getSuggestionId()).orElseThrow();
        assertThat(updated.getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("플러시 실패 시 Redis에 복구되고 재시도 시 반영되어야 한다")
    void 플러시_실패시_Redis에_복구되고_재시도시_반영된다() {
        Suggestion saved = suggestionRepository.save(Suggestion.builder()
                .title("제목")
                .content("내용")
                .category(SuggestionCategory.SYSTEM_ERROR)
                .isPrivate(false)
                .isAnonymous(true)
                .user(null)
                .build());

        viewCountService.increment(saved.getSuggestionId());

        doThrow(new RuntimeException("db error"))
                .when(suggestionRepository)
                .incrementViewCount(anyLong(), anyInt());

        viewCountService.flushToDatabase();

        String key = "suggestion:view:" + saved.getSuggestionId();
        String cached = stringRedisTemplate.opsForValue().get(key);
        assertThat(cached).isEqualTo("1");

        reset(suggestionRepository);
        viewCountService.flushToDatabase();

        entityManager.clear();
        Suggestion updated = suggestionRepository.findById(saved.getSuggestionId()).orElseThrow();
        assertThat(updated.getViewCount()).isEqualTo(1);
    }
}