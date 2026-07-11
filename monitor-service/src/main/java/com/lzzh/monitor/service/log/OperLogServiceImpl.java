package com.lzzh.monitor.service.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.OperLogPageRequest;
import com.lzzh.monitor.api.response.OperLogVo;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.SysOperLog;
import com.lzzh.monitor.dao.mapper.SysOperLogMapper;
import com.lzzh.monitor.service.convert.OperLogConverter;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class OperLogServiceImpl implements OperLogService {

    @Resource
    private SysOperLogMapper mapper;

    /**
     * 分页查询操作日志。
     *
     * @param query 分页与过滤条件
     * @return 操作日志分页结果
     */
    @Override
    public PageResult<OperLogVo> page(OperLogPageRequest query) {
        Page<SysOperLog> page = Pages.build(query);
        return Pages.toResult(mapper.selectPage(page, buildWrapper(query))).map(OperLogConverter::toVo);
    }

    /**
     * 按条件取全部操作日志（用于导出）。
     *
     * @param query 过滤条件
     * @return 符合条件的操作日志列表（不分页）
     */
    @Override
    public List<OperLogVo> listForExport(OperLogPageRequest query) {
        return mapper.selectList(buildWrapper(query)).stream().map(OperLogConverter::toVo).toList();
    }

    private LambdaQueryWrapper<SysOperLog> buildWrapper(OperLogPageRequest q) {
        LambdaQueryWrapper<SysOperLog> qw = new LambdaQueryWrapper<>();
        if (q != null) {
            if (StringUtils.hasText(q.getUsername())) {
                qw.eq(SysOperLog::getUsername, q.getUsername());
            }
            if (StringUtils.hasText(q.getAction())) {
                qw.eq(SysOperLog::getAction, q.getAction());
            }
            if (StringUtils.hasText(q.getModule())) {
                qw.eq(SysOperLog::getModule, q.getModule());
            }
            if (q.getStartTime() != null) {
                qw.ge(SysOperLog::getOperTime, q.getStartTime());
            }
            if (q.getEndTime() != null) {
                qw.le(SysOperLog::getOperTime, q.getEndTime());
            }
            if (StringUtils.hasText(q.getKeyword())) {
                qw.and(w -> w.like(SysOperLog::getTarget, q.getKeyword())
                        .or().like(SysOperLog::getDetail, q.getKeyword()));
            }
        }
        qw.orderByDesc(SysOperLog::getOperTime);
        return qw;
    }
}
