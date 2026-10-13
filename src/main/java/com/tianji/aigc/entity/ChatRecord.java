package com.tianji.aigc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_record")
public class ChatRecord implements Serializable {

    /**
     * 数据id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对话id
     */
    private String conversationId;

    /**
     * 对话数据，存放序列化后的消息json
     */
    private String data;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private Long creater;

    /**
     * 更新人
     */
    private Long updater;
}