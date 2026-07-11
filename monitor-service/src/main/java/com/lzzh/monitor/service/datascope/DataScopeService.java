package com.lzzh.monitor.service.datascope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.common.constant.Constants;
import com.lzzh.monitor.dao.entity.SysRole;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.InstanceGroupMapper;
import com.lzzh.monitor.dao.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 实例数据范围校验的唯一实现（角色管理-权限配置-数据范围 与 实例负责人A/B、所属分组"共同生效，取并集"）。
 *
 * <p>解析规则（后续调整数据范围逻辑只需改这一处，各处业务代码统一调用本组件，不再各自实现）：
 * <ol>
 *   <li>角色编码包含 {@code super_admin}，或该用户任一角色 {@code data_scope=all}
 *       → 不受限，返回 {@link DataScope#all()}；</li>
 *   <li>否则可见实例 = 本人为负责人A/B的实例
 *       ∪ 本人所属分组（负责人或成员）关联的实例
 *       ∪ 角色 {@code data_scope=group} 时指定分组关联的实例；</li>
 *   <li>角色 {@code data_scope=self}（默认值）不额外增加实例——负责人维度已在第 2 点统一并入，
 *       "仅本人负责"即代表不再叠加分组/全量范围。</li>
 * </ol>
 *
 * <p>用户未绑定任何角色，或角色未查到时，视为最小权限（仅本人负责的实例），避免误配置导致越权。
 */
@Service
public class DataScopeService {

    private final SysRoleMapper roleMapper;
    private final DbInstanceMapper instanceMapper;
    private final InstanceGroupMapper groupMapper;

    public DataScopeService(SysRoleMapper roleMapper, DbInstanceMapper instanceMapper, InstanceGroupMapper groupMapper) {
        this.roleMapper = roleMapper;
        this.instanceMapper = instanceMapper;
        this.groupMapper = groupMapper;
    }

    /**
     * 取当前线程绑定的登录用户（{@link CurrentUserHolder}）解析数据范围；
     * 未绑定（后台任务等系统调用场景）视为不受限。
     */
    public DataScope currentScope() {
        CurrentUserHolder.Current current = CurrentUserHolder.get();
        if (current == null) {
            return DataScope.all();
        }
        return resolve(current.userId(), current.roles());
    }

    /**
     * 解析指定用户的数据范围。
     *
     * @param userId    用户ID，为空时视为系统调用，返回不受限
     * @param roleCodes 用户角色编码集合（多角色取并集，与权限解析一致）
     */
    public DataScope resolve(Long userId, List<String> roleCodes) {
        if (userId == null) {
            return DataScope.all();
        }
        if (roleCodes != null && roleCodes.contains(Constants.SUPER_ADMIN_ROLE)) {
            return DataScope.all();
        }
        List<SysRole> roles = CollectionUtils.isEmpty(roleCodes)
                ? List.of()
                : roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getCode, roleCodes));

        Set<Long> groupScopeIds = new LinkedHashSet<>();
        for (SysRole role : roles) {
            String scope = role.getDataScope();
            if (scope == null || "all".equals(scope)) {
                return DataScope.all();
            }
            if ("group".equals(scope) && role.getDataScopeGroups() != null) {
                groupScopeIds.addAll(role.getDataScopeGroups());
            }
            // self：不额外增加分组，负责人维度统一在下方并入
        }

        Set<Long> visible = new LinkedHashSet<>(instanceMapper.selectOwnedInstanceIds(userId));

        Set<Long> effectiveGroupIds = new LinkedHashSet<>(groupMapper.selectGroupIdsForUser(userId));
        effectiveGroupIds.addAll(groupScopeIds);
        if (!effectiveGroupIds.isEmpty()) {
            visible.addAll(instanceMapper.selectIdsByGroupIds(effectiveGroupIds));
        }
        return DataScope.of(visible);
    }
}
