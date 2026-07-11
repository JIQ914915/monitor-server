package com.lzzh.monitor.service.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.MenuRequest;
import com.lzzh.monitor.api.response.SysMenuVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageParam;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.SysMenu;
import com.lzzh.monitor.dao.mapper.SysMenuMapper;
import com.lzzh.monitor.service.convert.MenuConverter;
import com.lzzh.monitor.service.support.Pages;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MenuServiceImpl implements MenuService {

    private final SysMenuMapper mapper;

    /**
     * 构造菜单管理服务实现。
     *
     * @param mapper 菜单 Mapper
     */
    public MenuServiceImpl(SysMenuMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 分页查询菜单，支持按名称/编码关键字过滤，并按排序号与主键升序排列。
     *
     * @param param 分页与关键字过滤条件
     * @return 菜单分页结果
     */
    @Override
    public PageResult<SysMenuVo> page(PageParam param) {
        Page<SysMenu> page = Pages.build(param);
        LambdaQueryWrapper<SysMenu> qw = new LambdaQueryWrapper<>();
        if (param != null && StringUtils.hasText(param.getKeyword())) {
            qw.and(w -> w.like(SysMenu::getName, param.getKeyword())
                    .or().like(SysMenu::getCode, param.getKeyword()));
        }
        qw.orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId);
        return Pages.toResult(mapper.selectPage(page, qw)).map(MenuConverter::toVo);
    }

    /**
     * 查询全部菜单（按排序号与主键升序）。
     *
     * @return 全部菜单列表
     */
    @Override
    public List<SysMenuVo> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId))
                .stream().map(MenuConverter::toVo).toList();
    }

    /**
     * 新增菜单：校验编码唯一，默认状态为 enabled，并写入创建/更新时间。
     *
     * @param request 菜单信息（含按钮权限点）
     * @return 新建菜单的主键 ID
     */
    @Override
    public Long create(MenuRequest request) {
        SysMenu menu = MenuConverter.toEntity(request);
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getCode, menu.getCode()));
        if (count != null && count > 0) {
            throw new BusinessException("菜单编码已存在: " + menu.getCode());
        }
        if (!StringUtils.hasText(menu.getStatus())) {
            menu.setStatus("enabled");
        }
        menu.setCreateTime(OffsetDateTime.now());
        menu.setUpdateTime(OffsetDateTime.now());
        mapper.insert(menu);
        return menu.getId();
    }

    /**
     * 修改菜单，并刷新更新时间。
     *
     * @param request 菜单信息（须含主键 ID）
     */
    @Override
    public void update(MenuRequest request) {
        SysMenu menu = MenuConverter.toEntity(request);
        menu.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(menu);
    }

    /**
     * 删除菜单。
     *
     * @param id 菜单主键 ID
     */
    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * 启用/停用菜单，并刷新更新时间。
     *
     * @param id     菜单主键 ID
     * @param status 目标状态：enabled 启用 / disabled 停用
     */
    @Override
    public void toggleStatus(Long id, String status) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setStatus(status);
        m.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(m);
    }
}
