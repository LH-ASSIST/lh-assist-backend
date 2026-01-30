package com.lh.assist.regulation.domain.enums.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApprovalStatus {
    WAITING("검토 대기", "#9E9E9E"), // 회색
    APPROVED("정상 승인", "#4CAF50"), // 초록
    REJECTED("반려", "#FF9800");      // 주황

    private final String description;
    private final String colorCode;
}