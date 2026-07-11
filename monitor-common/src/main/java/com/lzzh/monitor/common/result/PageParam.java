package com.lzzh.monitor.common.result;

import io.swagger.v3.oas.annotations.media.Schema;

/** 分页请求参数（各分页查询 DTO 的基类）。 */
@Schema(description = "通用分页请求参数")
public class PageParam {

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    private long pageNum = 1;

    @Schema(description = "每页条数", example = "10", defaultValue = "10")
    private long pageSize = 10;

    @Schema(description = "模糊搜索关键字", example = "mysql")
    private String keyword;

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
