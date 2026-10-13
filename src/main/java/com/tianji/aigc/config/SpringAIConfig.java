package com.tianji.aigc.config;

import com.tianji.aigc.advisor.RecordOptimizationAdvisor;
import com.tianji.aigc.memory.RedisChatMemoryRepository;
import com.tianji.aigc.memory.jdbc.JdbcChatMemoryRepository;
import com.tianji.aigc.memory.pg.PgChatMemoryRepository;
import com.tianji.aigc.service.ChatRecordService;
import com.tianji.aigc.tools.CourseTools;
import com.tianji.aigc.tools.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SpringAIConfig {

    @Value("${tj.ai.memory.max:100}")
    private Integer maxMessages;

    /**
     * 配置 ChatClient
     */
    /**
     * 配置 ChatClient
     */
    @Bean
    public ChatClient chatClient(@Qualifier("dashscopeChatModel") ChatModel dashScopeChatModel,
                                 Advisor loggerAdvisor,
                                 Advisor messageChatMemoryAdvisor,
                                 Advisor recordOptimizationAdvisor, // 记录优化
                                 CourseTools courseTools, // 课程工具
                                 OrderTools orderTools // 预下单工具

    ) {  // 日志记录器
        return ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(loggerAdvisor, messageChatMemoryAdvisor, recordOptimizationAdvisor) //添加 Advisor 功能增强
                // .defaultTools(courseTools, orderTools)
                .build();
    }

    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai.memory", name = "type", havingValue = "Redis", matchIfMissing = true)
    public ChatMemoryRepository redisChatMemoryRepository(StringRedisTemplate stringRedisTemplate) {
        return new RedisChatMemoryRepository(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai.memory", name = "type", havingValue = "MYSQL")
    public ChatMemoryRepository jdbcChatMemoryRepository(ChatRecordService chatRecordService) {
        return new JdbcChatMemoryRepository(chatRecordService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai.memory", name = "type", havingValue = "PG")
    public ChatMemoryRepository pgChatMemoryRepository(ChatRecordService chatRecordService) {
        return new PgChatMemoryRepository(chatRecordService);
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(this.maxMessages)
                .build();
    }

    @Bean
    public Advisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    public Advisor recordOptimizationAdvisor(ChatMemoryRepository chatMemoryRepository) {
        return new RecordOptimizationAdvisor(chatMemoryRepository);
    }

    @Bean
    public ChatClient openAiChatClient(@Qualifier("openAiChatModel") ChatModel openAiChatModel,
                                       Advisor loggerAdvisor  // 日志记录器
    ) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(loggerAdvisor)
                .build();
    }
}