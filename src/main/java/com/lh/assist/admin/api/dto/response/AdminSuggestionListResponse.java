package com.lh.assist.admin.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lh.assist.suggestion.domain.enums.SuggestionCategory;
import com.lh.assist.suggestion.domain.enums.SuggestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "건의사항 목록 응답(관리자)")
public class AdminSuggestionListResponse {
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
    @Schema(description = "조회수", example = "12")
    private final int viewCount;
    @Schema(description = "작성자 표시명", example = "공공주택본부 차장")
    @JsonProperty("writerDisplay")
    private final String writerDisplay;
    @Schema(description = "작성 일시", example = "2026-01-19T10:22:11")
    private final LocalDateTime createdAt;
}