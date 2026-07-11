package com.lzzh.monitor.service.group;

import com.lzzh.monitor.api.request.GroupRequest;
import com.lzzh.monitor.api.response.GroupOptionVo;
import com.lzzh.monitor.api.response.GroupVo;
import com.lzzh.monitor.common.result.PageParam;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 实例分组管理服务。 */
public interface InstanceGroupService {

    /**
     * 分页查询分组。
     *
     * @param param 分页与关键字条件
     * @return 分组分页结果
     */
    PageResult<GroupVo> page(PageParam param);

    /**
     * 全部分组（用于父分组下拉、实例表单分组多选）。
     *
     * @return 全部分组列表
     */
    List<GroupVo> listAll();

    /**
     * 分组选项（仅 id + name），供角色数据范围、下拉选择等使用，避免拉取完整分组数据。
     *
     * @return 分组选项列表
     */
    List<GroupOptionVo> listOptions();

    /**
     * 新增分组。
     *
     * @param group 分组信息
     * @return 新建分组的主键 ID
     */
    Long create(GroupRequest group);

    /**
     * 修改分组。
     *
     * @param group 分组信息（须含主键 ID）
     */
    void update(GroupRequest group);

    /**
     * 删除分组。
     *
     * @param id 分组主键 ID
     */
    void delete(Long id);
}
