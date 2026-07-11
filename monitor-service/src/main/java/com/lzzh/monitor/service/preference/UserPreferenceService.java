package com.lzzh.monitor.service.preference;

import java.util.Map;

/** 用户个性化偏好服务（账号级主题持久化，方案 §8.5）。 */
public interface UserPreferenceService {

    /**
     * 获取用户主题配置；无则返回 null（前端回落系统默认）。
     *
     * @param userId 用户主键 ID
     * @return 主题配置键值对，无则为 null
     */
    Map<String, Object> getTheme(Long userId);

    /**
     * 保存（不存在则新增）用户主题配置。
     *
     * @param userId 用户主键 ID
     * @param theme  主题配置键值对
     */
    void saveTheme(Long userId, Map<String, Object> theme);
}
