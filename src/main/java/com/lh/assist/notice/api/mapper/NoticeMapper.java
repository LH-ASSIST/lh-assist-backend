package com.lh.assist.notice.api.mapper;

import com.lh.assist.notice.api.dto.response.NoticeResponse;
import com.lh.assist.notice.domain.entity.Notice;

public class NoticeMapper {

    private NoticeMapper() {
    }

    public static NoticeResponse toResponse(
            Notice notice,
            int viewCount
    ) {
        return NoticeResponse.builder()
                .noticeId(notice.getNoticeId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .viewCount(viewCount)
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}