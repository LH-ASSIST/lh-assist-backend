package com.lh.assist.chatbot.application;

import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.chatbot.api.dto.response.ChatDocumentResponse;
import com.lh.assist.common.exception.ChatbotException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatDocumentService {

    private final DocumentRepository documentRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final UserRepository userRepository;

    /**
     * 로그인 사용자의 분석 완료 문서 목록을 조회한다.
     *
     * 최신 SUCCEEDED 분석 결과가 있는 문서만 반환한다.
     */
    @Transactional(readOnly = true)
    public List<ChatDocumentResponse> getSelectableDocuments(Long userId) {
        if (userId == null) {
            throw new ChatbotException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatbotException(ErrorCode.UNAUTHORIZED));

        return documentRepository.findAllAccessibleByUserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .filter(document -> document.getUser().equals(user))
                .map(this::toChatDocumentResponse)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private java.util.Optional<ChatDocumentResponse> toChatDocumentResponse(Document document) {
        AnalysisResult latestSucceeded = analysisResultRepository
                .findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(
                        document.getDocId(),
                        AnalysisResultStatus.SUCCEEDED
                )
                .orElse(null);
        if (latestSucceeded == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(ChatDocumentResponse.builder()
                .docId(document.getDocId())
                .title(document.getTitle())
                .docType(document.getDocType())
                .baseDate(document.getBaseDate())
                .analysisId(latestSucceeded.getAnalysisId())
                .totalRiskScore(latestSucceeded.getTotalRiskScore())
                .parsedJsonS3Key(latestSucceeded.getParsedJsonS3Key())
                .build());
    }
}