package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.api.request.DrilldownProfileSaveRequest;
import com.lzzh.monitor.api.response.DrilldownProfileVo;

import java.util.List;

/**
 * 告警下钻画像管理与匹配（§11.7 事件下钻）。
 * <p>画像按告警触发指标编码匹配（exact 优先于 prefix、prefix 长者优先），
 * 均未命中时回退兜底画像（match_rules 为空的 generic）。
 */
public interface AlertDrilldownProfileService {

    /** 查询全部画像（管理页列表，按 sort 排序）。 */
    List<DrilldownProfileVo> list();

    /** 新增或更新画像（id 为空新增）；内置画像不允许修改编码。 */
    Long save(DrilldownProfileSaveRequest req);

    /** 删除画像；内置画像不可删除。 */
    void delete(Long id);

    /** 启停画像。 */
    void toggle(Long id, boolean enabled);

    /**
     * 按触发指标编码匹配启用中的画像；未命中或 metricCode 为空时回退兜底画像。
     *
     * @return 命中的画像；画像库为空时返回 null（前端自行降级）
     */
    DrilldownProfileVo match(String metricCode);

    /** 按实例数据库类型隔离画像，避免同一 host.* 指标跨 MySQL/PostgreSQL 误匹配。 */
    DrilldownProfileVo match(String metricCode, String dbType);
}
