package com.lzzh.monitor.common.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 分页返回体（与前端 PageResult<T> 对应：{ list, total }）。 */
@Schema(description = "分页返回体")
public class PageResult<T> implements Serializable {

    @Schema(description = "当前页数据列表")
    private List<T> list;

    @Schema(description = "总记录数", example = "128")
    private long total;

    public PageResult() {
        this.list = Collections.emptyList();
        this.total = 0;
    }

    public PageResult(List<T> list, long total) {
        this.list = list;
        this.total = total;
    }

    public static <T> PageResult<T> of(List<T> list, long total) {
        return new PageResult<>(list, total);
    }

    /** 将分页内元素映射为另一类型（实体 -> VO），total 保持不变。 */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = list == null ? Collections.emptyList()
                : list.stream().map(mapper).collect(Collectors.toList());
        return new PageResult<>(mapped, total);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
