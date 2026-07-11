package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 知识库文章（富文本正文以 HTML 存储；分类/标签用于导航与检索）。 */
@Data
@TableName(value = "knowledge_article", autoResultMap = true)
public class KnowledgeArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 分类编码，如 fault/performance/practice/mysql/postgresql/backup。 */
    private String category;

    /** 标签集合，jsonb 存储。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /** 富文本正文（HTML）。 */
    private String content;

    private String author;

    private Integer views;

    private Integer likes;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("created_at")
    private OffsetDateTime createTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("updated_at")
    private OffsetDateTime updateTime;
}
