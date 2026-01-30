package com.lh.assist.notice.domain.repository;

import com.lh.assist.notice.domain.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    @Modifying
    @Query("update Notice n set n.viewCount = n.viewCount + :delta where n.noticeId = :id")
    int incrementViewCount(@Param("id") Long noticeId, @Param("delta") int delta);

    Page<Notice> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
            String title,
            String content,
            org.springframework.data.domain.Pageable pageable
    );

    Page<Notice> findByTitleContainingIgnoreCase(
            String title,
            org.springframework.data.domain.Pageable pageable
    );

    Page<Notice> findByContentContainingIgnoreCase(
            String content,
            org.springframework.data.domain.Pageable pageable
    );
}