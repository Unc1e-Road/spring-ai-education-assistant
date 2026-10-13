package com.tianji.aigc.memory.mongodb;

import cn.hutool.core.collection.CollStreamUtil;
import com.tianji.aigc.memory.MessageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

@RequiredArgsConstructor
public class MongoDBChatMemoryRepository implements ChatMemoryRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<String> findConversationIds() {
        var chatRecordList = this.mongoTemplate.findAll(ChatRecord.class);
        return CollStreamUtil.toList(chatRecordList, ChatRecord::getConversationId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Query query = Query.query(Criteria.where("conversationId").is(conversationId));
        var chatRecord = this.mongoTemplate.findOne(query, ChatRecord.class);
        if (null == chatRecord) {
            return List.of();
        }
        return CollStreamUtil.toList(chatRecord.getMessages(), MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        this.deleteByConversationId(conversationId);

        var chatRecord = ChatRecord.builder()
                .conversationId(conversationId)
                .messages(CollStreamUtil.toList(messages, MessageUtil::toJson))
                .build();

        this.mongoTemplate.save(chatRecord);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Query query = Query.query(Criteria.where("conversationId").is(conversationId));
        this.mongoTemplate.remove(query, ChatRecord.class);
    }
}