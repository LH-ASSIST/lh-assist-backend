package com.lh.assist.chatbot.domain.model;

import com.lh.assist.chatbot.domain.enums.RagReferenceType;

/**
 * rag_references JSON schema
 * Example:
 * {
 *   "type": "REGULATION",
 *   "id": 105,
 *   "title": "공공주택특별법 시행령 제3조",
 *   "score": 0.92,
 *   "url": "https://...",
 *   "snippet": "...",
 *   "chunkIndex": 3
 * }
 */
public record RagReference(
        RagReferenceType type,
        Long id,
        String title,
        Double score,
        String url,
        String snippet,
        Integer chunkIndex
) {
}