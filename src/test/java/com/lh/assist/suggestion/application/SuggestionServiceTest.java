package com.lh.assist.suggestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.suggestion.api.dto.request.SuggestionCreateRequest;
import com.lh.assist.suggestion.api.dto.request.SuggestionUpdateRequest;
import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.enums.SuggestionCategory;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private SuggestionViewCountService viewCountService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SuggestionService suggestionService;

    @Test
    @DisplayName("공개 글 조회는 바로 조회되며 조회수 증가가 호출되어야 한다")
    void 공개_글_조회시_조회수_증가_호출() {
        UserPrincipal principal = new UserPrincipal(1L, "user@lh.com", "USER");
        Suggestion suggestion = suggestion(false, owner(1L));
        when(suggestionRepository.findById(10L)).thenReturn(Optional.of(suggestion));

        Suggestion result = suggestionService.getSuggestion(principal.userId(), principal.isAdmin(), 10L);

        assertThat(result).isSameAs(suggestion);
        verify(viewCountService).increment(10L);
    }

    @Test
    @DisplayName("비공개 글을 본인이 조회하면 조회수 증가가 호출되어야 한다")
    void 비공개_글_본인_조회시_허용() {
        UserPrincipal principal = new UserPrincipal(1L, "user@lh.com", "USER");
        Suggestion suggestion = suggestion(true, owner(1L));
        when(suggestionRepository.findById(11L)).thenReturn(Optional.of(suggestion));

        Suggestion result = suggestionService.getSuggestion(principal.userId(), principal.isAdmin(), 11L);

        assertThat(result).isSameAs(suggestion);
        verify(viewCountService).increment(11L);
    }

    @Test
    @DisplayName("비공개 글을 다른 사용자가 조회하면 접근 거부가 발생해야 한다")
    void 비공개_글_타인_조회시_거부() {
        UserPrincipal principal = new UserPrincipal(2L, "other@lh.com", "USER");
        Suggestion suggestion = suggestion(true, owner(1L));
        when(suggestionRepository.findById(12L)).thenReturn(Optional.of(suggestion));

        Long userId = principal.userId();
        boolean isAdmin = principal.isAdmin();

        assertThatThrownBy(() -> suggestionService.getSuggestion(userId, isAdmin, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(viewCountService, never()).increment(any());
    }

    @Test
    @DisplayName("관리자는 일반 사용자와 동일하게 공개/본인 글만 조회해야 한다")
    void 관리자_목록도_공개와_본인만() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@lh.com", "ADMIN");
        Pageable pageable = PageRequest.of(0, 10);
        Page<Suggestion> expected = new PageImpl<>(java.util.List.of(suggestion(false, owner(1L))));
        when(suggestionRepository.findVisibleByUserId(1L, pageable)).thenReturn(expected);

        Page<Suggestion> result = suggestionService.getSuggestions(principal.userId(), principal.isAdmin(), pageable);

        assertThat(result).isSameAs(expected);
        verify(suggestionRepository).findVisibleByUserId(1L, pageable);
    }

    @Test
    @DisplayName("일반 사용자는 공개/본인 글 목록만 조회해야 한다")
    void 일반_사용자_목록은_공개와_본인만() {
        UserPrincipal principal = new UserPrincipal(5L, "user@lh.com", "USER");
        Pageable pageable = PageRequest.of(0, 10);
        Page<Suggestion> expected = new PageImpl<>(java.util.List.of(suggestion(false, owner(5L))));
        when(suggestionRepository.findVisibleByUserId(5L, pageable)).thenReturn(expected);

        Page<Suggestion> result = suggestionService.getSuggestions(principal.userId(), principal.isAdmin(), pageable);

        assertThat(result).isSameAs(expected);
        verify(suggestionRepository).findVisibleByUserId(5L, pageable);
    }

    @Test
    @DisplayName("익명 작성 시 익명 플래그가 저장되어야 한다")
    void 익명_작성시_플래그_저장() {
        User user = owner(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        SuggestionCreateRequest request = createRequest(true);
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Suggestion result = suggestionService.createSuggestion(1L, request);

        assertThat(result.isAnonymous()).isTrue();
        assertThat(result.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("비익명 작성 시 익명 플래그가 해제되어야 한다")
    void 비익명_작성시_플래그_해제() {
        User user = owner(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        SuggestionCreateRequest request = createRequest(false);
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Suggestion result = suggestionService.createSuggestion(2L, request);

        assertThat(result.isAnonymous()).isFalse();
        assertThat(result.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 인증 오류가 발생해야 한다")
    void 사용자_없으면_인증_오류() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        SuggestionCreateRequest request = createRequest(true);

        assertThatThrownBy(() -> suggestionService.createSuggestion(99L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(suggestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성자가 건의사항을 수정하면 필드가 갱신되어야 한다")
    void 작성자_수정시_필드_갱신() {
        User owner = owner(1L);
        Suggestion suggestion = suggestion(false, owner);
        when(suggestionRepository.findById(10L)).thenReturn(Optional.of(suggestion));

        SuggestionUpdateRequest request = new SuggestionUpdateRequest(
                "수정 제목",
                "수정 내용",
                SuggestionCategory.RECOMMENDATION,
                true,
                false
        );

        Suggestion updated = suggestionService.updateSuggestion(1L, false, 10L, request);

        assertThat(updated.getTitle()).isEqualTo("수정 제목");
        assertThat(updated.getContent()).isEqualTo("수정 내용");
        assertThat(updated.getCategory()).isEqualTo(SuggestionCategory.RECOMMENDATION);
        assertThat(updated.isPrivate()).isTrue();
        assertThat(updated.isAnonymous()).isFalse();
    }

    @Test
    @DisplayName("작성자가 아니면 건의사항 수정이 거부되어야 한다")
    void 작성자_아니면_수정_거부() {
        User owner = owner(1L);
        Suggestion suggestion = suggestion(false, owner);
        when(suggestionRepository.findById(11L)).thenReturn(Optional.of(suggestion));

        SuggestionUpdateRequest request = new SuggestionUpdateRequest(
                "수정 제목",
                "수정 내용",
                SuggestionCategory.RECOMMENDATION,
                true,
                false
        );

        assertThatThrownBy(() -> suggestionService.updateSuggestion(2L, false, 11L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("작성자가 삭제하면 건의사항이 삭제되고 조회수 캐시가 정리되어야 한다")
    void 작성자_삭제시_삭제_및_캐시_정리() {
        User owner = owner(1L);
        Suggestion suggestion = suggestion(false, owner);
        when(suggestionRepository.findById(12L)).thenReturn(Optional.of(suggestion));

        suggestionService.deleteSuggestion(1L, false, 12L);

        verify(suggestionRepository).delete(suggestion);
        verify(viewCountService).evict(12L);
    }

    @Test
    @DisplayName("작성자가 아니면 삭제가 거부되어야 한다")
    void 작성자_아니면_삭제_거부() {
        User owner = owner(1L);
        Suggestion suggestion = suggestion(false, owner);
        when(suggestionRepository.findById(13L)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> suggestionService.deleteSuggestion(2L, false, 13L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(suggestionRepository, never()).delete(any());
        verify(viewCountService, never()).evict(anyLong());
    }

    private static SuggestionCreateRequest createRequest(boolean anonymous) {
        return new SuggestionCreateRequest(
                "제목",
                "내용",
                SuggestionCategory.SYSTEM_ERROR,
                false,
                anonymous
        );
    }

    private static Suggestion suggestion(
            boolean isPrivate,
            User owner
    ) {
        return Suggestion.builder()
                .title("제목")
                .content("내용")
                .category(SuggestionCategory.SYSTEM_ERROR)
                .isPrivate(isPrivate)
                .isAnonymous(true)
                .user(owner)
                .build();
    }

    private static User owner(Long userId) {
        return User.builder()
                .userId(userId)
                .email("user@lh.com")
                .password("hashed")
                .name("Tester")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build();
    }

}
