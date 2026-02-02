package com.lh.assist.suggestion.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.suggestion.domain.enums.SuggestionCategory;
import com.lh.assist.suggestion.domain.enums.SuggestionStatus;
import com.lh.assist.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "suggestions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suggestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_id")
    private Long suggestionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private SuggestionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SuggestionStatus status = SuggestionStatus.WAITING;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder
    public Suggestion(
            String title,
            String content,
            SuggestionCategory category,
            boolean isPrivate,
            boolean isAnonymous,
            User user
    ) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.isPrivate = isPrivate;
        this.isAnonymous = isAnonymous;
        this.user = user;
    }

    public void answer(String answerContent) {
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
        this.status = SuggestionStatus.ANSWERED;
    }

    public void update(
            String title,
            String content,
            SuggestionCategory category,
            boolean isPrivate,
            boolean isAnonymous
    ) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.isPrivate = isPrivate;
        this.isAnonymous = isAnonymous;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Suggestion that)) return false;
        return suggestionId != null && suggestionId.equals(that.suggestionId);
    }

    @Override
    public int hashCode() {
        return suggestionId != null ? suggestionId.hashCode() : getClass().hashCode();
    }
}