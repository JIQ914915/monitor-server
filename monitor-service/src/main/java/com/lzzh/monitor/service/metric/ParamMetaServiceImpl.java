package com.lzzh.monitor.service.metric;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.ParamPageRequest;
import com.lzzh.monitor.api.response.ParamMetaVo;
import com.lzzh.monitor.dao.entity.MysqlParamMeta;
import com.lzzh.monitor.dao.mapper.MysqlParamMetaMapper;
import com.lzzh.monitor.service.support.Pages;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ParamMetaServiceImpl implements ParamMetaService {

    private final MysqlParamMetaMapper mapper;

    public ParamMetaServiceImpl(MysqlParamMetaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ParamMetaVo> listAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<MysqlParamMeta>()
                        .orderByAsc(MysqlParamMeta::getCategory, MysqlParamMeta::getParamName)
        ).stream().map(this::toVo).toList();
    }

    @Override
    public List<ParamMetaVo> listByCategory(String category) {
        LambdaQueryWrapper<MysqlParamMeta> wrapper = new LambdaQueryWrapper<MysqlParamMeta>()
                .orderByAsc(MysqlParamMeta::getParamName);
        if (StringUtils.hasText(category)) {
            wrapper.eq(MysqlParamMeta::getCategory, category);
        }
        return mapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    @Override
    public Page<MysqlParamMeta> page(ParamPageRequest req) {
        Page<MysqlParamMeta> page = Pages.build(req);
        LambdaQueryWrapper<MysqlParamMeta> qw = new LambdaQueryWrapper<MysqlParamMeta>()
                .orderByAsc(MysqlParamMeta::getCategory, MysqlParamMeta::getParamName);
        if (StringUtils.hasText(req.getKeyword())) {
            String kw = "%" + req.getKeyword().trim() + "%";
            qw.and(w -> w.like(MysqlParamMeta::getParamName, kw)
                    .or().like(MysqlParamMeta::getDescription, kw));
        }
        if (StringUtils.hasText(req.getCategory())) {
            qw.eq(MysqlParamMeta::getCategory, req.getCategory().trim());
        }
        return mapper.selectPage(page, qw);
    }

    private ParamMetaVo toVo(MysqlParamMeta entity) {
        ParamMetaVo vo = new ParamMetaVo();
        vo.setParamName(entity.getParamName());
        vo.setDisplayName(entity.getDisplayName());
        vo.setCategory(entity.getCategory());
        vo.setIsDynamic(entity.getIsDynamic());
        vo.setUnit(entity.getUnit());
        vo.setDescription(entity.getDescription());
        vo.setMinVersion(entity.getMinVersion());
        vo.setMaxVersion(entity.getMaxVersion());
        return vo;
    }
}
