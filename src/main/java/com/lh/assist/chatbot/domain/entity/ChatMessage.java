package com.lh.assist.chatbot.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.chatbot.domain.model.RagReference;
import com.lh.assist.user.domain.entity.User;
import jakarta.persistence.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /**
     * RAG 근거 데이터 (JSON)
     * PostgreSQL의 jsonb 타입을 사용
     * Java에서는 JSON 문자열로 저장하고, 필요 시 ObjectMapper로 변환하여 사용
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rag_references", columnDefinition = "jsonb")
    private List<RagReference> ragReferences;

    @Column(name = "item_id")
    private Long itemId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder
    public ChatMessage(
            String sessionId,
            String question,
            String answer,
            List<RagReference> ragReferences,
            Long itemId,
            User user
    ) {
        this.sessionId = sessionId;
        this.question = question;
        this.answer = answer;
        this.ragReferences = ragReferences;
        this.itemId = itemId;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage that)) return false;
        return messageId != null && messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() {
        return messageId != null ? messageId.hashCode() : getClass().hashCode();
    }
}