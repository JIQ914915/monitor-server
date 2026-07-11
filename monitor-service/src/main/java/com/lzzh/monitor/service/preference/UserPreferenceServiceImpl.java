package com.lzzh.monitor.service.preference;

import com.lzzh.monitor.dao.entity.UserPreference;
import com.lzzh.monitor.dao.mapper.UserPreferenceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    @Resource
    private UserPreferenceMapper mapper;

    /**
     * 获取用户主题配置；无记录则返回 null。
     *
     * @param userId 用户主键 ID
     * @return 主题配置键值对，无则为 null
     */
    @Override
    public Map<String, Object> getTheme(Long userId) {
        UserPreference pref = mapper.selectById(userId);
        return pref == null ? null : pref.getTheme();
    }

    /**
     * 保存用户主题配置：主键为 user_id，存在则更新，否则插入。
     *
     * @param userId 用户主键 ID
     * @param theme  主题配置键值对
     */
    @Override
    public void saveTheme(Long userId, Map<String, Object> theme) {
        UserPreference pref = new UserPreference();
        pref.setUserId(userId);
        pref.setTheme(theme);
        pref.setUpdateTime(OffsetDateTime.now());
        // 主键为 user_id：存在则更新，否则插入
        if (mapper.selectById(userId) == null) {
            mapper.insert(pref);
        } else {
            mapper.updateById(pref);
        }
    }
}
