package com.lzzh.monitor.service.log;

import com.lzzh.monitor.api.request.OperLogPageRequest;
import com.lzzh.monitor.api.response.OperLogVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 操作日志查询服务（审计只读 + 导出）。 */
public interface OperLogService {

    /**
     * 分页查询操作日志。
     *
     * @param query 分页与过滤条件
     * @return 操作日志分页结果
     */
    PageResult<OperLogVo> page(OperLogPageRequest query);

    /**
     * 按条件取全部（用于导出）。
     *
     * @param query 过滤条件
     * @return 符合条件的操作日志列表（不分页）
     */
    List<OperLogVo> listForExport(OperLogPageRequest query);
}
