package com.lzzh.monitor.service.group;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.GroupRequest;
import com.lzzh.monitor.api.response.GroupOptionVo;
import com.lzzh.monitor.api.response.GroupVo;
import com.lzzh.monitor.common.result.PageParam;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.InstanceGroup;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.InstanceGroupMapper;
import com.lzzh.monitor.service.convert.GroupConverter;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class InstanceGroupServiceImpl implements InstanceGroupService {

    @Resource
    private InstanceGroupMapper mapper;
    @Resource
    private DbInstanceMapper instanceMapper;

    /**
     * 分页查询分组，并补充每个分组关联的实例数。
     *
     * @param param 分页与关键字条件
     * @return 分组分页结果
     */
    @Override
    public PageResult<GroupVo> page(PageParam param) {
        Page<InstanceGroup> page = Pages.build(param);
        LambdaQueryWrapper<InstanceGroup> qw = new LambdaQueryWrapper<>();
        if (param != null && StringUtils.hasText(param.getKeyword())) {
            qw.like(InstanceGroup::getName, param.getKeyword());
        }
        qw.orderByDesc(InstanceGroup::getId);
        PageResult<InstanceGroup> result = Pages.toResult(mapper.selectPage(page, qw));
        return result.map(g -> GroupConverter.toVo(g, countInstances(g.getId())));
    }

    /**
     * 查询全部分组（按 ID 升序）。
     *
     * @return 全部分组列表
     */
    @Override
    public List<GroupVo> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<InstanceGroup>().orderByAsc(InstanceGroup::getId))
                .stream().map(g -> GroupConverter.toVo(g, null)).toList();
    }

    /**
     * 分组选项（仅 id + name），按 ID 升序。
     *
     * @return 分组选项列表
     */
    @Override
    public List<GroupOptionVo> listOptions() {
        return mapper.selectList(new LambdaQueryWrapper<InstanceGroup>().orderByAsc(InstanceGroup::getId))
                .stream().map(g -> new GroupOptionVo(g.getId(), g.getName())).toList();
    }

    /**
     * 新增分组并填充创建/更新时间。
     *
     * @param request 分组信息
     * @return 新建分组的主键 ID
     */
    @Override
    public Long create(GroupRequest request) {
        InstanceGroup group = GroupConverter.toEntity(request);
        group.setCreateTime(OffsetDateTime.now());
        group.setUpdateTime(OffsetDateTime.now());
        mapper.insert(group);
        return group.getId();
    }

    /**
     * 修改分组并刷新更新时间。
     *
     * @param request 分组信息（须含主键 ID）
     */
    @Override
    public void update(GroupRequest request) {
        InstanceGroup group = GroupConverter.toEntity(request);
        group.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(group);
    }

    /**
     * 删除分组。
     *
     * @param id 分组主键 ID
     */
    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /** 统计 db_instance.group_ids 中包含该分组ID 的实例数（PG jsonb 包含查询）。 */
    private Long countInstances(Long groupId) {
        QueryWrapper<DbInstance> qw = new QueryWrapper<>();
        qw.apply("group_ids @> {0}::jsonb", "[" + groupId + "]");
        return instanceMapper.selectCount(qw);
    }
}
