package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 知识文章（响应）。 */
@Data
@Schema(description = "知识文章信息（响应）")
public class KnowledgeArticleVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "文章标题", example = "MySQL 主从延迟排查手册")
    private String title;

    @Schema(description = "文章分类", example = "运维手册")
    private String category;

    @Schema(description = "标签列表", example = "[\"MySQL\",\"主从\"]")
    private List<String> tags;

    @Schema(description = "富文本正文（HTML）", example = "<p>正文内容</p>")
    private String content;

    @Schema(description = "作者", example = "张三")
    private String author;

    @Schema(description = "浏览次数", example = "128")
    private Integer views;

    @Schema(description = "点赞次数", example = "12")
    private Integer likes;

    @Schema(description = "创建时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
