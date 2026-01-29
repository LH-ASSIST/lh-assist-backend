package com.lh.assist.notice.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "공지사항 조회 응답")
public class NoticeResponse {
    @Schema(description = "공지사항 ID", example = "1")
    private final Long noticeId;
    @Schema(description = "제목", example = "서비스 점검 안내")
    private final String title;
    @Schema(description = "내용", example = "2026-02-01 02:00~05:00 점검 예정입니다.")
    private final String content;
    @Schema(description = "조회수", example = "12")
    private final int viewCount;
    @Schema(description = "작성 일시", example = "2026-01-19T10:22:11")
    private final LocalDateTime createdAt;
    @Schema(description = "수정 일시", example = "2026-01-19T10:22:11")
    private final LocalDateTime updatedAt;
}