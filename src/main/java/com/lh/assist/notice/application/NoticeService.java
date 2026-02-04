package com.lh.assist.notice.application;

import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.NoticeException;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.notice.api.dto.request.NoticeCreateRequest;
import com.lh.assist.notice.api.dto.request.NoticeSearchType;
import com.lh.assist.notice.api.dto.request.NoticeUpdateRequest;
import com.lh.assist.notice.domain.entity.Notice;
import com.lh.assist.notice.domain.repository.NoticeRepository;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final String NOTICE_ID_PREFIX = "noticeId:";

    private final NoticeRepository noticeRepository;
    private final NoticeViewCountService viewCountService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * 공지사항을 상세 조회하고 조회수를 1 증가시킨다
     *
     * @param noticeId 조회할 공지사항 ID
     * @return 조회된 공지사항 엔티티
     */
    @Transactional(readOnly = true)
    public Notice getNotice(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeException(ErrorCode.NOTICE_NOT_FOUND));
        viewCountService.increment(noticeId);
        return notice;
    }

    /**
     * 공지사항 목록을 조회한다
     *
     * @param pageable 페이징 정보
     * @return 조회된 공지사항 페이지
     */
    @Transactional(readOnly = true)
    public Page<Notice> getNotices(Pageable pageable) {
        return noticeRepository.findAll(pageable);
    }

    /**
     * 공지사항을 등록한다
     *
     * @param request 등록할 공지사항 정보
     * @return 생성된 공지사항 엔티티
     */
    @Transactional
    public Notice createNotice(
            NoticeCreateRequest request,
            Long actorId
    ) {
        Notice notice = Notice.builder()
                .title(request.title())
                .content(request.content())
                .build();
        Notice saved = noticeRepository.save(notice);
        User actor = getUserById(actorId);
        auditLogService.log(
                AuditActionType.NOTICE_CREATED,
                AuditTargetType.NOTICE,
                saved.getNoticeId(),
                NOTICE_ID_PREFIX + saved.getNoticeId(),
                actor
        );
        return saved;
    }

    /**
     * 공지사항을 수정한다
     *
     * @param noticeId 수정할 공지사항 ID
     * @param request 수정할 공지사항 정보
     * @return 수정된 공지사항 엔티티
     */
    @Transactional
    public Notice updateNotice(
            Long noticeId,
            NoticeUpdateRequest request,
            Long actorId
    ) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeException(ErrorCode.NOTICE_NOT_FOUND));
        notice.update(request.title(), request.content());
        User actor = getUserById(actorId);
        auditLogService.log(
                AuditActionType.NOTICE_UPDATED,
                AuditTargetType.NOTICE,
                notice.getNoticeId(),
                NOTICE_ID_PREFIX + notice.getNoticeId(),
                actor
        );
        return notice;
    }

    /**
     * 공지사항을 삭제한다
     *
     * @param noticeId 삭제할 공지사항 ID
     */
    @Transactional
    public void deleteNotice(
            Long noticeId,
            Long actorId
    ) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeException(ErrorCode.NOTICE_NOT_FOUND));
        noticeRepository.delete(notice);
        viewCountService.evict(noticeId);
        User actor = getUserById(actorId);
        auditLogService.log(
                AuditActionType.NOTICE_DELETED,
                AuditTargetType.NOTICE,
                noticeId,
                NOTICE_ID_PREFIX + noticeId,
                actor
        );
    }

    /**
     * 현재 조회수를 조회한다 (DB + Redis 누적값)
     *
     * @param noticeId 공지사항 ID
     * @param baseCount DB 조회수
     * @return 합산된 조회수
     */
    public int getViewCount(
            Long noticeId,
            int baseCount
    ) {
        return viewCountService.getViewCount(noticeId, baseCount);
    }

    /**
     * 목록 조회용 조회수 증가분(Delta) 맵을 조회한다
     *
     * @param noticeIds 공지사항 ID 목록
     * @return 공지사항 ID -> 조회수 증가분
     */
    public Map<Long, Integer> getViewCountDeltas(List<Long> noticeIds) {
        return viewCountService.getViewCountDeltas(noticeIds);
    }

    private User getUserById(Long userId) {
        if (userId == null) {
            throw new NoticeException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoticeException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 공지사항을 검색한다
     *
     * @param type 검색 타입
     * @param keyword 검색어
     * @param pageable 페이징 정보
     * @return 검색된 공지사항 페이지
     */
    @Transactional(readOnly = true)
    public Page<Notice> searchNotices(
            NoticeSearchType type,
            String keyword,
            Pageable pageable
    ) {
        if (keyword == null || keyword.isBlank()) {
            return noticeRepository.findAll(pageable);
        }
        String term = keyword.trim();
        if (type == null) {
            return noticeRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                    term,
                    term,
                    pageable
            );
        }
        return switch (type) {
            case TITLE -> noticeRepository.findByTitleContainingIgnoreCase(term, pageable);
            case CONTENT -> noticeRepository.findByContentContainingIgnoreCase(term, pageable);
            case ALL -> noticeRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                    term,
                    term,
                    pageable
            );
        };
    }
}
