package com.lh.assist.notice.api;

import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.notice.api.docs.NoticeCreateDocs;
import com.lh.assist.notice.api.docs.NoticeDeleteDocs;
import com.lh.assist.notice.api.docs.NoticeGetDocs;
import com.lh.assist.notice.api.docs.NoticeListDocs;
import com.lh.assist.notice.api.docs.NoticeUpdateDocs;
import com.lh.assist.notice.api.dto.request.NoticeCreateRequest;
import com.lh.assist.notice.api.dto.request.NoticeUpdateRequest;
import com.lh.assist.notice.api.dto.response.NoticeResponse;
import com.lh.assist.notice.api.mapper.NoticeMapper;
import com.lh.assist.notice.application.NoticeService;
import com.lh.assist.notice.domain.entity.Notice;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notice")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    @NoticeListDocs
    public ResponseEntity<ApiResponse<Page<NoticeResponse>>> listNotices(
            @ParameterObject Pageable pageable
    ) {
        Page<Notice> notices = noticeService.getNotices(pageable);
        List<Long> ids = notices.getContent().stream()
                .map(Notice::getNoticeId)
                .toList();
        Map<Long, Integer> deltas = noticeService.getViewCountDeltas(ids);
        Page<NoticeResponse> responses = notices.map(notice -> {
            int viewCount = notice.getViewCount() + deltas.getOrDefault(notice.getNoticeId(), 0);
            return NoticeMapper.toResponse(notice, viewCount);
        });
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/{noticeId}")
    @PreAuthorize("isAuthenticated()")
    @NoticeGetDocs
    public ResponseEntity<ApiResponse<NoticeResponse>> getNotice(
            @PathVariable Long noticeId
    ) {
        Notice notice = noticeService.getNotice(noticeId);
        int viewCount = noticeService.getViewCount(noticeId, notice.getViewCount());
        NoticeResponse response = NoticeMapper.toResponse(notice, viewCount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @NoticeCreateDocs
    public ResponseEntity<ApiResponse<NoticeResponse>> createNotice(
            @Validated @RequestBody NoticeCreateRequest request
    ) {
        Notice created = noticeService.createNotice(request);
        NoticeResponse response = NoticeMapper.toResponse(created, created.getViewCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @PutMapping("/{noticeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @NoticeUpdateDocs
    public ResponseEntity<ApiResponse<NoticeResponse>> updateNotice(
            @PathVariable Long noticeId,
            @Validated @RequestBody NoticeUpdateRequest request
    ) {
        Notice updated = noticeService.updateNotice(noticeId, request);
        int viewCount = noticeService.getViewCount(noticeId, updated.getViewCount());
        NoticeResponse response = NoticeMapper.toResponse(updated, viewCount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{noticeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @NoticeDeleteDocs
    public ResponseEntity<ApiResponse<Void>> deleteNotice(
            @PathVariable Long noticeId
    ) {
        noticeService.deleteNotice(noticeId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent());
    }
}
