package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识文章分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识文章分页查询请求")
public class KnowledgePageRequest extends PageParam {

    /** 分类过滤（null / all 表示全部）。 */
    @Schema(description = "分类过滤，null 或 all 表示全部", example = "运维手册")
    private String category;
}
