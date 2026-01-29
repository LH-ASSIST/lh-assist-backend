package com.lh.assist.chatbot.domain.repository;

import com.lh.assist.chatbot.domain.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
}