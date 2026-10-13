package com.tianji.aigc.controller;

import com.tianji.aigc.dto.ChatDTO;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.aigc.vo.TemplateVO;
import com.tianji.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 聊天
     * @param chatDTO
     * @return
     */
    @NoWrapper // 标记结果不进行包装
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chat(@RequestBody ChatDTO chatDTO) {
        return this.chatService.chat(chatDTO.getQuestion(), chatDTO.getSessionId());
    }

    /**
     * 停止生成对话
     */
    @PostMapping("/stop")
    public void stop(@RequestBody String sessionId) {
        this.chatService.stop(sessionId);
    }

    /**
     * 聊天文本
     * @param question
     * @return
     */
    @PostMapping("/text")
    public String chatText(@RequestBody String question) {
        return this.chatService.chatText(question);
    }

    private static final TemplateVO TEMPLATE_VO = new TemplateVO();

    @GetMapping("/templates")
    public TemplateVO getTemplates() {
        return TEMPLATE_VO;
    }
}
