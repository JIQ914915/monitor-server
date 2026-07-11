package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 知识文章新增/编辑（请求）。 */
@Data
@Schema(description = "知识文章新增/编辑请求")
public class KnowledgeArticleRequest {

    @Schema(description = "文章主键 ID；新增时为空，编辑时必填", example = "1")
    private Long id;

    @Schema(description = "文章标题", example = "MySQL 主从延迟排查手册", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入文章标题")
    private String title;

    @Schema(description = "文章分类", example = "运维手册", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择分类")
    private String category;

    @Schema(description = "标签列表", example = "[\"MySQL\",\"主从\"]")
    private List<String> tags;

    /** 富文本正文（HTML）。 */
    @Schema(description = "富文本正文（HTML）", example = "<p>正文内容</p>")
    private String content;

    @Schema(description = "作者", example = "张三")
    private String author;
}
