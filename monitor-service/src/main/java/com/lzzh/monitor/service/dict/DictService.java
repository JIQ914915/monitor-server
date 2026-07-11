package com.lzzh.monitor.service.dict;

import com.lzzh.monitor.api.request.DictItemRequest;
import com.lzzh.monitor.api.request.DictTypeRequest;
import com.lzzh.monitor.api.response.DictItemVo;
import com.lzzh.monitor.api.response.DictTypeVo;

import java.util.List;

/** 数据字典服务：字典类型 + 字典项维护，供前端下拉/标签渲染。 */
public interface DictService {

    /**
     * 查询全部字典类型（左侧导航用）。
     *
     * @return 字典类型列表
     */
    List<DictTypeVo> listTypes();

    /**
     * 新增字典类型。
     *
     * @param request 字典类型信息
     * @return 新建字典类型的主键 ID
     */
    Long createType(DictTypeRequest request);

    /**
     * 修改字典类型；编码变更时同步其下字典项的 dictType。
     *
     * @param request 字典类型信息（须含主键 ID）
     */
    void updateType(DictTypeRequest request);

    /**
     * 删除字典类型（连同其下字典项）。
     *
     * @param id 字典类型主键 ID
     */
    void deleteType(Long id);

    /**
     * 按字典类型编码查询字典项（排序升序）。
     *
     * @param dictType 字典类型编码
     * @return 该字典类型下的字典项列表
     */
    List<DictItemVo> listItems(String dictType);

    /**
     * 新增字典项。
     *
     * @param request 字典项信息
     * @return 新建字典项的主键 ID
     */
    Long createItem(DictItemRequest request);

    /**
     * 修改字典项。
     *
     * @param request 字典项信息（须含主键 ID）
     */
    void updateItem(DictItemRequest request);

    /**
     * 删除字典项。
     *
     * @param id 字典项主键 ID
     */
    void deleteItem(Long id);
}
