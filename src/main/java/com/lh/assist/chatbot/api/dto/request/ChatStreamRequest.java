package com.lh.assist.chatbot.api.dto.request;

public record ChatStreamRequest(
        String sessionId,
        String question,
        Long itemId,
        Long analysisResultId,
        String parsedJsonS3Key,
        Long docId,
        Long analysisId,
        Boolean documentSelected,
        Boolean analysisSelected,
        Boolean useRag
) {
    public Long resolvedAnalysisId() {
        return analysisId != null ? analysisId : analysisResultId;
    }

    public boolean hasParsedJsonS3Key() {
        return parsedJsonS3Key != null && !parsedJsonS3Key.isBlank();
    }

    public boolean hasSelection() {
        return Boolean.TRUE.equals(documentSelected)
                || Boolean.TRUE.equals(analysisSelected)
                || docId != null
                || resolvedAnalysisId() != null
                || itemId != null;
    }
}