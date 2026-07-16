package com.lzzh.monitor.service.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.common.result.PageParam;
import com.lzzh.monitor.common.result.PageResult;

/**
 * 分页转换收口：PageParam → MyBatis-Plus Page，IPage → 统一 PageResult。
 * 各 service 复用，避免重复的入参兜底与出参装配逻辑。
 */
public final class Pages {

    /** 单页最大条数，防止前端传超大 pageSize 拖垮查询。 */
    private static final long MAX_PAGE_SIZE = 200L;

    private Pages() {
    }

    /** 由分页入参构建 MP Page（含页码/页大小兜底与上限钳制）。 */
    public static <T> Page<T> build(PageParam param) {
        PageWindow window = window(param);
        return Page.of(window.pageNum(), window.pageSize());
    }

    /** 构建 JDBC/MyBatis XML 查询使用的分页窗口。 */
    public static PageWindow window(PageParam param) {
        long pageNum = param == null || param.getPageNum() < 1 ? 1 : param.getPageNum();
        long requestedSize = param == null || param.getPageSize() < 1 ? 10 : param.getPageSize();
        int pageSize = (int) Math.min(requestedSize, MAX_PAGE_SIZE);
        long base = pageNum - 1;
        long offset = base > Long.MAX_VALUE / pageSize ? Long.MAX_VALUE : base * pageSize;
        return new PageWindow(pageNum, pageSize, offset);
    }

    public record PageWindow(long pageNum, int pageSize, long offset) {
    }

    /** 由 MP 分页结果装配统一 PageResult。 */
    public static <T> PageResult<T> toResult(IPage<T> page) {
        return PageResult.of(page.getRecords(), page.getTotal());
    }
}
