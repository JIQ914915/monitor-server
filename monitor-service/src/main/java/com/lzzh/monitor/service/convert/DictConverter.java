package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.DictItemRequest;
import com.lzzh.monitor.api.request.DictTypeRequest;
import com.lzzh.monitor.api.response.DictItemVo;
import com.lzzh.monitor.api.response.DictTypeVo;
import com.lzzh.monitor.dao.entity.SysDictItem;
import com.lzzh.monitor.dao.entity.SysDictType;

/** 字典实体 ↔ DTO 转换。 */
public final class DictConverter {

    private DictConverter() {
    }

    /**
     * 字典类型实体转响应 VO。
     *
     * @param e 字典类型实体，可为 null
     * @return 字典类型响应 VO，入参为 null 时返回 null
     */
    public static DictTypeVo toVo(SysDictType e) {
        if (e == null) {
            return null;
        }
        DictTypeVo v = new DictTypeVo();
        v.setId(e.getId());
        v.setDictType(e.getDictType());
        v.setDictName(e.getDictName());
        v.setStatus(e.getStatus());
        v.setType(e.getType());
        v.setRemark(e.getRemark());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * 字典类型请求转实体。
     *
     * @param r 字典类型请求，可为 null
     * @return 字典类型实体，入参为 null 时返回 null
     */
    public static SysDictType toEntity(DictTypeRequest r) {
        if (r == null) {
            return null;
        }
        SysDictType e = new SysDictType();
        e.setId(r.getId());
        e.setDictType(r.getDictType());
        e.setDictName(r.getDictName());
        e.setStatus(r.getStatus());
        e.setType(r.getType());
        e.setRemark(r.getRemark());
        return e;
    }

    /**
     * 字典项实体转响应 VO。
     *
     * @param e 字典项实体，可为 null
     * @return 字典项响应 VO，入参为 null 时返回 null
     */
    public static DictItemVo toVo(SysDictItem e) {
        if (e == null) {
            return null;
        }
        DictItemVo v = new DictItemVo();
        v.setId(e.getId());
        v.setDictType(e.getDictType());
        v.setItemValue(e.getItemValue());
        v.setItemLabel(e.getItemLabel());
        v.setTagType(e.getTagType());
        v.setSort(e.getSort());
        v.setStatus(e.getStatus());
        v.setRemark(e.getRemark());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * 字典项请求转实体。
     *
     * @param r 字典项请求，可为 null
     * @return 字典项实体，入参为 null 时返回 null
     */
    public static SysDictItem toEntity(DictItemRequest r) {
        if (r == null) {
            return null;
        }
        SysDictItem e = new SysDictItem();
        e.setId(r.getId());
        e.setDictType(r.getDictType());
        e.setItemValue(r.getItemValue());
        e.setItemLabel(r.getItemLabel());
        e.setTagType(r.getTagType());
        e.setSort(r.getSort());
        e.setStatus(r.getStatus());
        e.setRemark(r.getRemark());
        return e;
    }
}
