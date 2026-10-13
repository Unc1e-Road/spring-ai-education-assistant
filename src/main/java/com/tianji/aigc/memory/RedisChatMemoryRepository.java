package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

public class RedisChatMemoryRepository implements ChatMemoryRepository, MyChatMemoryRepository {

    public static final String DEFAULT_PREFIX = "CHAT:";

    private final StringRedisTemplate stringRedisTemplate;
    private final String prefix;

    public RedisChatMemoryRepository(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, DEFAULT_PREFIX);
    }

    public RedisChatMemoryRepository(StringRedisTemplate stringRedisTemplate, String prefix) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.prefix = prefix;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = this.stringRedisTemplate.keys(this.prefix + "*");
        if (null == keys) {
            return List.of();
        }
        return StreamUtil.of(keys)
                .map(key -> StrUtil.replace(key, this.prefix, ""))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        var messages = listOps.range(0, -1);
        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.notEmpty(messages, "消息列表不能为空");
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        this.deleteByConversationId(conversationId);
        messages.forEach(message -> listOps.rightPush(MessageUtil.toJson(message)));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var redisKey = this.getKey(conversationId);
        this.stringRedisTemplate.delete(redisKey);
    }

    private String getKey(String conversationId) {
        return prefix + conversationId;
    }

    /**
     * 根据对话ID优化对话记录，删除最后的2条消息，因为这条消息是从路由智能体存储的，请求由后续的智能体处理
     * 为了确保历史消息的完整性，所以需要将中间转发的消息清理掉
     *
     * @param conversationId 对话的唯一标识符
     */
    public void optimization(String conversationId) {
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        // 从Redis列表右侧弹出2个元素
        listOps.rightPop(2);
    }
}