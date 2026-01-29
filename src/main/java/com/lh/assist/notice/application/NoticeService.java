package com.lh.assist.notice.application;

import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.NoticeException;
import com.lh.assist.notice.api.dto.request.NoticeCreateRequest;
import com.lh.assist.notice.api.dto.request.NoticeUpdateRequest;
import com.lh.assist.notice.domain.entity.Notice;
import com.lh.assist.notice.domain.repository.NoticeRepository;
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

    private final NoticeRepository noticeRepository;
    private final NoticeViewCountService viewCountService;

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
    public Notice createNotice(NoticeCreateRequest request) {
        Notice notice = Notice.builder()
                .title(request.title())
                .content(request.content())
                .build();
        return noticeRepository.save(notice);
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
            NoticeUpdateRequest request
    ) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeException(ErrorCode.NOTICE_NOT_FOUND));
        notice.update(request.title(), request.content());
        return notice;
    }

    /**
     * 공지사항을 삭제한다
     *
     * @param noticeId 삭제할 공지사항 ID
     */
    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeException(ErrorCode.NOTICE_NOT_FOUND));
        noticeRepository.delete(notice);
        viewCountService.evict(noticeId);
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
}
