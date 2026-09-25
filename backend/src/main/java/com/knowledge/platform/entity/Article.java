package com.knowledge.platform.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "articles")
public class Article {
    @Id
    private String id;

    @Indexed
    private String columnId;

    @TextIndexed(weight = 3)
    private String title;

    @TextIndexed(weight = 2)
    private String summary;

    @TextIndexed(weight = 1)
    private String content;

    private Integer sequence;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 当前请求用户是否可以阅读全文。非持久化字段，由接口按订阅关系动态计算。
     */
    @Transient
    private Boolean readable;

    /**
     * 不可读时的原因：NOT_SUBSCRIBED 从未订阅；EXPIRED_BEFORE 订阅过期后才更新的文章。
     */
    @Transient
    private String lockReason;
}
