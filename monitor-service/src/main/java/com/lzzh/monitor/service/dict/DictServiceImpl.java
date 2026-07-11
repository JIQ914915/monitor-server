package com.lzzh.monitor.service.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.DictItemRequest;
import com.lzzh.monitor.api.request.DictTypeRequest;
import com.lzzh.monitor.api.response.DictItemVo;
import com.lzzh.monitor.api.response.DictTypeVo;
import com.lzzh.monitor.common.constant.Constants;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.SysDictItem;
import com.lzzh.monitor.dao.entity.SysDictType;
import com.lzzh.monitor.dao.mapper.SysDictItemMapper;
import com.lzzh.monitor.dao.mapper.SysDictTypeMapper;
import com.lzzh.monitor.service.convert.DictConverter;
import com.lzzh.monitor.service.datascope.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class DictServiceImpl implements DictService {

    private final SysDictTypeMapper typeMapper;
    private final SysDictItemMapper itemMapper;

    public DictServiceImpl(SysDictTypeMapper typeMapper, SysDictItemMapper itemMapper) {
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 查询全部字典类型（按 ID 升序）。
     *
     * @return 字典类型列表
     */
    @Override
    public List<DictTypeVo> listTypes() {
        return typeMapper.selectList(new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getId))
                .stream().map(DictConverter::toVo).toList();
    }

    /**
     * 新增字典类型，编码重复时抛出业务异常，状态为空默认 enabled。
     * 非超管新建固定为自定义类型。
     *
     * @param request 字典类型信息
     * @return 新建字典类型的主键 ID
     */
    @Override
    public Long createType(DictTypeRequest request) {
        Long dup = typeMapper.selectCount(
                new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getDictType, request.getDictType()));
        if (dup != null && dup > 0) {
            throw new BusinessException("字典类型编码已存在: " + request.getDictType());
        }
        SysDictType e = DictConverter.toEntity(request);
        if (!StringUtils.hasText(e.getStatus())) {
            e.setStatus("enabled");
        }
        e.setType(resolveTypeForCreate(request.getType()));
        e.setCreateTime(OffsetDateTime.now());
        e.setUpdateTime(OffsetDateTime.now());
        typeMapper.insert(e);
        return e.getId();
    }

    /**
     * 修改字典类型；类型编码变更时同步其下字典项的 dictType。
     * 系统级字典仅超管可修改。
     *
     * @param request 字典类型信息（须含主键 ID）
     */
    @Override
    public void updateType(DictTypeRequest request) {
        SysDictType old = typeMapper.selectById(request.getId());
        if (old == null) {
            throw new BusinessException("字典类型不存在: " + request.getId());
        }
        assertMutable(old);
        SysDictType e = DictConverter.toEntity(request);
        e.setType(resolveTypeForUpdate(old, request.getType()));
        e.setUpdateTime(OffsetDateTime.now());
        typeMapper.updateById(e);
        // 类型编码变更时，同步其下字典项的 dict_type
        if (!old.getDictType().equals(request.getDictType())) {
            SysDictItem patch = new SysDictItem();
            patch.setDictType(request.getDictType());
            itemMapper.update(patch,
                    new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictType, old.getDictType()));
        }
    }

    /**
     * 删除字典类型（连同其下所有字典项），类型不存在时静默返回。
     * 系统级字典仅超管可删除。
     *
     * @param id 字典类型主键 ID
     */
    @Override
    @Transactional
    public void deleteType(Long id) {
        SysDictType type = typeMapper.selectById(id);
        if (type == null) {
            return;
        }
        assertMutable(type);
        itemMapper.delete(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictType, type.getDictType()));
        typeMapper.deleteById(id);
    }

    /**
     * 按字典类型编码查询字典项（sort、id 升序）。
     *
     * @param dictType 字典类型编码
     * @return 该字典类型下的字典项列表
     */
    @Override
    public List<DictItemVo> listItems(String dictType) {
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, dictType)
                        .orderByAsc(SysDictItem::getSort).orderByAsc(SysDictItem::getId))
                .stream().map(DictConverter::toVo).toList();
    }

    /**
     * 新增字典项，同类型下字典值重复时抛出业务异常，状态/排序为空时给默认值。
     * 所属字典类型为系统级时仅超管可新增。
     *
     * @param request 字典项信息
     * @return 新建字典项的主键 ID
     */
    @Override
    public Long createItem(DictItemRequest request) {
        assertMutable(requireTypeByCode(request.getDictType()));
        Long dup = itemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, request.getDictType())
                .eq(SysDictItem::getItemValue, request.getItemValue()));
        if (dup != null && dup > 0) {
            throw new BusinessException("该字典类型下已存在相同字典值: " + request.getItemValue());
        }
        SysDictItem e = DictConverter.toEntity(request);
        if (!StringUtils.hasText(e.getStatus())) {
            e.setStatus("enabled");
        }
        if (e.getSort() == null) {
            e.setSort(0);
        }
        e.setCreateTime(OffsetDateTime.now());
        e.setUpdateTime(OffsetDateTime.now());
        itemMapper.insert(e);
        return e.getId();
    }

    /**
     * 修改字典项。所属字典类型为系统级时仅超管可修改。
     *
     * @param request 字典项信息（须含主键 ID）
     */
    @Override
    public void updateItem(DictItemRequest request) {
        SysDictItem existing = itemMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException("字典项不存在: " + request.getId());
        }
        String dictType = StringUtils.hasText(request.getDictType()) ? request.getDictType() : existing.getDictType();
        assertMutable(requireTypeByCode(dictType));
        SysDictItem e = DictConverter.toEntity(request);
        e.setUpdateTime(OffsetDateTime.now());
        itemMapper.updateById(e);
    }

    /**
     * 删除字典项。所属字典类型为系统级时仅超管可删除。
     *
     * @param id 字典项主键 ID
     */
    @Override
    public void deleteItem(Long id) {
        SysDictItem item = itemMapper.selectById(id);
        if (item == null) {
            return;
        }
        assertMutable(requireTypeByCode(item.getDictType()));
        itemMapper.deleteById(id);
    }

    private SysDictType requireTypeByCode(String dictType) {
        SysDictType type = typeMapper.selectOne(
                new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getDictType, dictType));
        if (type == null) {
            throw new BusinessException("字典类型不存在: " + dictType);
        }
        return type;
    }

    private void assertMutable(SysDictType type) {
        if (isSystemScope(type) && !isSuperAdmin()) {
            throw new BusinessException("系统级字典不可修改，如需调整请联系超级管理员");
        }
    }

    private boolean isSystemScope(SysDictType type) {
        return type != null && Constants.DICT_SCOPE_SYSTEM.equals(type.getType());
    }

    private boolean isSuperAdmin() {
        CurrentUserHolder.Current current = CurrentUserHolder.get();
        return current != null && current.roles() != null
                && current.roles().contains(Constants.SUPER_ADMIN_ROLE);
    }

    private String resolveTypeForCreate(String requestedType) {
        if (!isSuperAdmin()) {
            return Constants.DICT_SCOPE_CUSTOM;
        }
        return StringUtils.hasText(requestedType) ? requestedType : Constants.DICT_SCOPE_CUSTOM;
    }

    private String resolveTypeForUpdate(SysDictType old, String requestedType) {
        if (!isSuperAdmin()) {
            return old.getType();
        }
        return StringUtils.hasText(requestedType) ? requestedType : old.getType();
    }
}
