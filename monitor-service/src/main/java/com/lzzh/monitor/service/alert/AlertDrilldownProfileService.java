package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.api.request.DrilldownProfileSaveRequest;
import com.lzzh.monitor.api.response.DrilldownProfileVo;

import java.util.List;

/**
 * 告警下钻画像管理与匹配（§11.7 事件下钻）。
 * <p>画像按数据库类型与告警触发指标编码匹配（exact 优先于 prefix、prefix 长者优先），
 * 均未命中时仅回退当前数据库类型的兜底画像（match_rules 为空的 generic）。
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

    /** 按实例数据库类型隔离画像；类型为空时返回 null，禁止跨库匹配。 */
    DrilldownProfileVo match(String metricCode, String dbType);
}