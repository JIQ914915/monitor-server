package com.lzzh.monitor.collector.alert;

import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.InstanceGroup;
import com.lzzh.monitor.dao.entity.SysUser;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.InstanceGroupMapper;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 根据实例负责人和所属分组解析告警通知联系人。 */
@Service
public class AlertContactResolver {

    private final DbInstanceMapper dbInstanceMapper;
    private final InstanceGroupMapper instanceGroupMapper;
    private final SysUserMapper sysUserMapper;

    public AlertContactResolver(DbInstanceMapper dbInstanceMapper,
                                InstanceGroupMapper instanceGroupMapper,
                                SysUserMapper sysUserMapper) {
        this.dbInstanceMapper = dbInstanceMapper;
        this.instanceGroupMapper = instanceGroupMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public ContactTargets resolve(Long instanceId) {
        if (instanceId == null) {
            return ContactTargets.EMPTY;
        }
        DbInstance instance = dbInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return ContactTargets.EMPTY;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        addIfNotNull(userIds, instance.getOwnerAId());
        addIfNotNull(userIds, instance.getOwnerBId());
        if (instance.getGroupIds() != null && !instance.getGroupIds().isEmpty()) {
            List<InstanceGroup> groups = instanceGroupMapper.selectByIds(instance.getGroupIds());
            for (InstanceGroup group : groups) {
                addIfNotNull(userIds, group.getOwnerId());
                if (group.getMemberIds() != null) {
                    userIds.addAll(group.getMemberIds());
                }
            }
        }
        if (userIds.isEmpty()) {
            return ContactTargets.EMPTY;
        }
        List<SysUser> users = sysUserMapper.selectByIds(userIds);
        List<String> emails = new ArrayList<>();
        List<String> phones = new ArrayList<>();
        for (SysUser user : users) {
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                continue;
            }
            if (StringUtils.hasText(user.getEmail())) {
                emails.add(user.getEmail().trim());
            }
            if (StringUtils.hasText(user.getPhone())) {
                phones.add(user.getPhone().trim());
            }
        }
        return new ContactTargets(distinct(emails), distinct(phones));
    }

    private static void addIfNotNull(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private static List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    public record ContactTargets(List<String> emails, List<String> phones) {
        private static final ContactTargets EMPTY = new ContactTargets(List.of(), List.of());
    }
}
