package com.lh.assist.chatbot.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    /**
     * 챗봇 대화를 위한 세션 ID를 생성한다.
     *
     * @return 생성된 세션 ID
     */
    public String createSessionId() {
        return UUID.randomUUID().toString();
    }
}
