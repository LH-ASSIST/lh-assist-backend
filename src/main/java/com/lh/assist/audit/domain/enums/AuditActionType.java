package com.lh.assist.audit.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditActionType {
    DOCUMENT_UPLOAD("문서 업로드"),
    DOCUMENT_DELETE("문서 삭제"),
    ANALYSIS_REQUESTED("분석 요청"),
    ANALYSIS_CALLBACK_SUCCEEDED("분석 완료"),
    ANALYSIS_CALLBACK_FAILED("분석 실패"),
    ANALYSIS_SQS_SEND_FAILED("SQS 전송 실패"),
    DOCUMENT_APPROVAL_ASSIGNED("승인자 지정"),
    DOCUMENT_APPROVAL_REVIEWED("승인 처리"),
    NOTICE_CREATED("공지 등록"),
    NOTICE_UPDATED("공지 수정"),
    NOTICE_DELETED("공지 삭제"),
    SUGGESTION_ANSWERED("건의 답변"),
    DELETE_USER("사용자 삭제");

    private final String description;
}