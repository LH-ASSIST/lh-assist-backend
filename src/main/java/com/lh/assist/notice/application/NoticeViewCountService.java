package com.lh.assist.notice.application;

import com.lh.assist.common.viewcount.AbstractViewCountService;
import com.lh.assist.notice.domain.repository.NoticeRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeViewCountService extends AbstractViewCountService {

    private final NoticeRepository noticeRepository;

    public NoticeViewCountService(
            StringRedisTemplate stringRedisTemplate,
            NoticeRepository noticeRepository
    ) {
        super(stringRedisTemplate);
        this.noticeRepository = noticeRepository;
    }

    @Override
    protected String viewKeyPrefix() {
        return "notice:view:";
    }

    @Override
    protected String viewKeySet() {
        return "notice:view:keys";
    }

    @Override
    protected int incrementViewCount(Long id, int delta) {
        return noticeRepository.incrementViewCount(id, delta);
    }

    @Override
    protected String flushFailLogMessage() {
        return "공지사항 조회수 반영에 실패했습니다. key={}, delta={}";
    }

    @Override
    protected String deletedLogMessage() {
        return "삭제된 공지사항으로 조회수 반영이 중단되었습니다. key={}";
    }

    @Scheduled(fixedDelayString = "${app.notice.view-count.flush-interval-ms:60000}")
    @Transactional
    public void flushToDatabase() {
        flushToDatabaseInternal();
    }
}