package com.timetable.api.chat;

import com.timetable.api.chat.ChatMessages.ChatReply;
import com.timetable.api.chat.ChatMessages.ChatRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public ChatReply chat(@RequestBody ChatRequest request) {
        return service.answer(request.conversationId(), request.text(), request.teacherId());
    }
}
