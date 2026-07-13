package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.api.request.CollectLogQueryRequest;
import com.lzzh.monitor.api.request.CollectTaskQueryRequest;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.dao.entity.CollectLog;
import com.lzzh.monitor.dao.mapper.CollectLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 采集任务及运行日志分页查询接口。 */
@Tag(name = "采集日志", description = "采集运行结果查询：任务分页 + 历史日志分页")
@RestController
@RequestMapping("/api/v1/collect-logs")
public class CollectLogController {

    @Resource
    private CollectLogMapper collectLogMapper;

    @Operation(summary = "采集任务分页", description = "分页返回数据库实例/主机各频率的最新采集状态")
    @PostMapping("/tasks")
    public Result<PageResult<Map<String, Object>>> tasks(
            @RequestBody(required = false) CollectTaskQueryRequest req) {
        CollectTaskQueryRequest query = req == null ? new CollectTaskQueryRequest() : req;
        int pageNum = positiveOrDefault(query.getPageNum(), 1);
        int pageSize = pageSize(query.getPageSize());
        int offset = (pageNum - 1) * pageSize;
        String keyword = blankToNull(query.getKeyword());
        String dbType = blankToNull(query.getDbType());
        String frequency = blankToNull(query.getFrequency());
        String status = blankToNull(query.getStatus());

        long total = collectLogMapper.countTasks(keyword, dbType, frequency, status);
        if (total == 0) {
            return Result.ok(PageResult.of(List.of(), 0));
        }
        List<Map<String, Object>> rows = collectLogMapper.selectTaskPage(
                keyword, dbType, frequency, status, offset, pageSize);
        for (Map<String, Object> row : rows) {
            String freq = String.valueOf(row.get("frequency"));
            boolean hostTask = "host".equals(row.get("targetType"));
            row.put("frequencyLabel", hostTask ? "主机分钟级（1m）" : freqLabel(freq));
            long total24h = toLong(row.get("total24h"));
            long success24h = toLong(row.get("success24h"));
            row.put("successRate", total24h > 0 ? (int) (success24h * 100 / total24h) : 100);
        }
        return Result.ok(PageResult.of(rows, total));
    }

    @Operation(summary = "采集任务统计", description = "返回当前过滤条件下的频率及运行状态统计")
    @PostMapping("/tasks/stats")
    public Result<Map<String, Object>> taskStats(
            @RequestBody(required = false) CollectTaskQueryRequest req) {
        CollectTaskQueryRequest query = req == null ? new CollectTaskQueryRequest() : req;
        return Result.ok(collectLogMapper.selectTaskStats(
                blankToNull(query.getKeyword()), blankToNull(query.getDbType()),
                blankToNull(query.getFrequency()), blankToNull(query.getStatus())));
    }

    @Operation(summary = "采集历史日志分页", description = "分页返回单实例或单主机、单频率的采集记录")
    @PostMapping("/query")
    public Result<PageResult<CollectLog>> logs(@Valid @RequestBody CollectLogQueryRequest req) {
        if ((req.getInstanceId() == null) == (req.getHostId() == null)) {
            throw new BusinessException("instanceId 和 hostId 必须且只能传一个");
        }
        int pageNum = positiveOrDefault(req.getPageNum(), 1);
        int pageSize = pageSize(req.getPageSize());
        int offset = (pageNum - 1) * pageSize;
        long total = collectLogMapper.countRecent(
                req.getInstanceId(), req.getHostId(), req.getFrequency());
        if (total == 0) {
            return Result.ok(PageResult.of(List.of(), 0));
        }
        List<CollectLog> rows = collectLogMapper.selectRecent(
                req.getInstanceId(), req.getHostId(), req.getFrequency(), offset, pageSize);
        return Result.ok(PageResult.of(rows, total));
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static int pageSize(Integer value) {
        return Math.min(positiveOrDefault(value, 20), 100);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String freqLabel(String freq) {
        return switch (freq) {
            case "1m" -> "分钟级（1m）";
            case "1h" -> "小时级（1h）";
            case "1d" -> "天级（1d）";
            default -> freq;
        };
    }

    private static long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        try { return val == null ? 0L : Long.parseLong(val.toString()); }
        catch (Exception e) { return 0L; }
    }
}