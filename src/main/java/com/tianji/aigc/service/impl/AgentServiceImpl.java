package com.tianji.aigc.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.tianji.aigc.agent.AbstractAgent;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ROUTE")
public class AgentServiceImpl implements ChatService {

    private final ChatClient openAiChatClient;
    private final SystemPromptConfig systemPromptConfig;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 先通过路由智能体，分析用户的意图，再执行后面的逻辑
        var result = this.findAgentByType(AgentTypeEnum.ROUTE).process(question, sessionId);
        var agentTypeEnum = AgentTypeEnum.agentNameOf(result);

        var agent = this.findAgentByType(agentTypeEnum);
        if (agent == null) {
            // 找不到对应的智能体，直接返回结果
            var chatEventVO = ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(result)
                    .build();
            return Flux.just(chatEventVO, AbstractAgent.STOP_EVENT);
        }
        // 执行智能体的逻辑
        return agent.processStream(question, sessionId);
    }

    /**
     * 文本对话：走独立的 OpenAI 协议模型，做无状态的文本处理（回复问答、润色、总结等）
     *
     * @param question 待处理的文本
     * @return 处理后的文本
     */
    @Override
    public String chatText(String question) {
        return this.openAiChatClient.prompt()
                .system(promptSystem -> promptSystem.text(this.systemPromptConfig.getTextSystemMessage().get()))
                .user(question)
                .call()
                .content();
    }

    /**
     * 根据代理类型查找对应的Agent实例
     *
     * @param agentTypeEnum 要查找的代理类型
     * @return 与给定类型匹配的Agent实例，如果未找到或类型为null则返回null
     */
    private Agent findAgentByType(AgentTypeEnum agentTypeEnum) {
        if (agentTypeEnum == null) {
            return null;
        }
        var beans = SpringUtil.getBeansOfType(Agent.class);
        // 遍历所有Agent Bean查找匹配类型
        for (var agent : beans.values()) {
            if (agentTypeEnum == agent.getAgentType()) {
                return agent;
            }
        }
        return null;
    }

    /**
     * 停止生成
     *
     * @param sessionId 会话ID
     */
    @Override
    public void stop(String sessionId) {
        this.findAgentByType(AgentTypeEnum.ROUTE).stop(sessionId);
    }
}
