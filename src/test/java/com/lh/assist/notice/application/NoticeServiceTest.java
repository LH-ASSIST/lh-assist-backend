package com.lh.assist.notice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.notice.api.dto.request.NoticeCreateRequest;
import com.lh.assist.notice.api.dto.request.NoticeSearchType;
import com.lh.assist.notice.api.dto.request.NoticeUpdateRequest;
import com.lh.assist.notice.domain.entity.Notice;
import com.lh.assist.notice.domain.repository.NoticeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private NoticeViewCountService viewCountService;

    @InjectMocks
    private NoticeService noticeService;

    @Test
    @DisplayName("공지사항 조회 시 조회수 증가가 호출되어야 한다")
    void 공지사항_조회시_조회수_증가_호출() {
        Notice notice = notice("제목", "내용");
        when(noticeRepository.findById(10L)).thenReturn(Optional.of(notice));

        Notice result = noticeService.getNotice(10L);

        assertThat(result).isSameAs(notice);
        verify(viewCountService).increment(10L);
    }

    @Test
    @DisplayName("공지사항이 없으면 NOT_FOUND가 발생해야 한다")
    void 공지사항_없으면_NOT_FOUND() {
        when(noticeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.getNotice(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);

        verify(viewCountService, never()).increment(anyLong());
    }

    @Test
    @DisplayName("공지사항을 생성하면 저장되어야 한다")
    void 공지사항_생성() {
        NoticeCreateRequest request = new NoticeCreateRequest("제목", "내용");
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notice created = noticeService.createNotice(request);

        assertThat(created.getTitle()).isEqualTo("제목");
        assertThat(created.getContent()).isEqualTo("내용");
    }

    @Test
    @DisplayName("공지사항을 수정하면 필드가 갱신되어야 한다")
    void 공지사항_수정() {
        Notice notice = notice("이전 제목", "이전 내용");
        when(noticeRepository.findById(10L)).thenReturn(Optional.of(notice));

        NoticeUpdateRequest request = new NoticeUpdateRequest("수정 제목", "수정 내용");

        Notice updated = noticeService.updateNotice(10L, request);

        assertThat(updated.getTitle()).isEqualTo("수정 제목");
        assertThat(updated.getContent()).isEqualTo("수정 내용");
    }

    @Test
    @DisplayName("공지사항 수정 대상이 없으면 NOT_FOUND가 발생해야 한다")
    void 공지사항_수정_대상_없음() {
        when(noticeRepository.findById(10L)).thenReturn(Optional.empty());

        NoticeUpdateRequest request = new NoticeUpdateRequest("수정 제목", "수정 내용");

        assertThatThrownBy(() -> noticeService.updateNotice(10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("공지사항 삭제 시 엔티티 삭제와 조회수 캐시 정리가 호출되어야 한다")
    void 공지사항_삭제() {
        Notice notice = notice("제목", "내용");
        when(noticeRepository.findById(10L)).thenReturn(Optional.of(notice));

        noticeService.deleteNotice(10L);

        verify(noticeRepository).delete(notice);
        verify(viewCountService).evict(10L);
    }

    private static Notice notice(String title, String content) {
        return Notice.builder()
                .title(title)
                .content(content)
                .build();
    }

    @Test
    @DisplayName("검색 타입이 TITLE이면 제목만 검색해야 한다")
    void 검색_제목() {
        Page<Notice> page = new PageImpl<>(List.of(notice("공지", "내용")));
        when(noticeRepository.findByTitleContainingIgnoreCase("공지", Pageable.ofSize(10)))
                .thenReturn(page);

        Page<Notice> result = noticeService.searchNotices(NoticeSearchType.TITLE, "공지", Pageable.ofSize(10));

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("검색 타입이 CONTENT이면 내용만 검색해야 한다")
    void 검색_내용() {
        Page<Notice> page = new PageImpl<>(List.of(notice("공지", "내용")));
        when(noticeRepository.findByContentContainingIgnoreCase("내용", Pageable.ofSize(10)))
                .thenReturn(page);

        Page<Notice> result = noticeService.searchNotices(NoticeSearchType.CONTENT, "내용", Pageable.ofSize(10));

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("검색 타입이 ALL이면 제목+내용으로 검색해야 한다")
    void 검색_전체() {
        Page<Notice> page = new PageImpl<>(List.of(notice("공지", "내용")));
        when(noticeRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase("공지", "공지", Pageable.ofSize(10)))
                .thenReturn(page);

        Page<Notice> result = noticeService.searchNotices(NoticeSearchType.ALL, "공지", Pageable.ofSize(10));

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("검색 타입이 null이면 제목+내용으로 검색해야 한다")
    void 검색_타입_널() {
        Page<Notice> page = new PageImpl<>(List.of(notice("공지", "내용")));
        when(noticeRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase("공지", "공지", Pageable.ofSize(10)))
                .thenReturn(page);

        Page<Notice> result = noticeService.searchNotices(null, "공지", Pageable.ofSize(10));

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("검색어가 비어있으면 전체 목록을 반환해야 한다")
    void 검색어_비어있음() {
        Page<Notice> page = new PageImpl<>(List.of(notice("공지", "내용")));
        when(noticeRepository.findAll(Pageable.ofSize(10))).thenReturn(page);

        Page<Notice> result = noticeService.searchNotices(NoticeSearchType.ALL, "  ", Pageable.ofSize(10));

        assertThat(result).isSameAs(page);
    }

}