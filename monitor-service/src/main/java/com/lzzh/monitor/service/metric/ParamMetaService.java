package com.lzzh.monitor.service.metric;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.ParamPageRequest;
import com.lzzh.monitor.api.response.ParamMetaVo;
import com.lzzh.monitor.dao.entity.MysqlParamMeta;

import java.util.List;

/** MySQL 配置参数元数据查询服务（配置 Tab 说明列）。 */
public interface ParamMetaService {

    /**
     * 查询所有参数元数据（按分类 + 参数名排序）。
     *
     * @return 全量参数元数据列表
     */
    List<ParamMetaVo> listAll();

    /**
     * 按分类查询参数元数据。
     *
     * @param category 分类（connection / innodb / logging / security / general）
     * @return 该分类下的参数元数据列表
     */
    List<ParamMetaVo> listByCategory(String category);

    /**
     * 分页查询参数元数据（真实 DB 分页，用于配置 Tab 服务端分页接口）。
     *
     * @param req 分页请求（keyword 模糊匹配 paramName/description，category 精确过滤）
     * @return MyBatis-Plus 分页结果（含 total / records）
     */
    Page<MysqlParamMeta> page(ParamPageRequest req);
}
