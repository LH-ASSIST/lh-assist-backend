package com.lh.assist.suggestion.api.dto;

import com.lh.assist.suggestion.domain.SuggestionCategory;
import com.lh.assist.suggestion.domain.SuggestionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
@Schema(description = "건의사항 조회 응답")
public class SuggestionResponse {
    @Schema(description = "건의사항 ID", example = "1")
    private final Long suggestionId;
    @Schema(description = "제목", example = "로그인 오류 해결 요청")
    private final String title;
    @Schema(description = "내용", example = "로그인 시 500 에러가 발생합니다.")
    private final String content;
    @Schema(description = "카테고리", example = "SYSTEM_ERROR")
    private final SuggestionCategory category;
    @Schema(description = "답변 상태", example = "WAITING")
    private final SuggestionStatus status;
    @Schema(description = "비공개 여부", example = "false")
    @JsonProperty("isPrivate")
    private final boolean isPrivate;
    @Schema(description = "답변 내용", example = "확인 후 수정하겠습니다.")
    private final String answerContent;
    @Schema(description = "답변 일시", example = "2026-01-19T10:22:11")
    private final LocalDateTime answeredAt;
    @Schema(description = "조회수", example = "12")
    private final int viewCount;
    @Schema(description = "익명 여부", example = "true")
    @JsonProperty("isAnonymous")
    private final boolean isAnonymous;
    @Schema(description = "작성자 표시명", example = "공공주택본부 차장")
    @JsonProperty("writerDisplay")
    private final String writerDisplay;
    @Schema(description = "작성자 ID", example = "10")
    @JsonProperty("userId")
    private final Long userId;
    @Schema(description = "작성 일시", example = "2026-01-19T10:22:11")
    private final LocalDateTime createdAt;
    @Schema(description = "수정 일시", example = "2026-01-19T10:22:11")
    private final LocalDateTime updatedAt;
}