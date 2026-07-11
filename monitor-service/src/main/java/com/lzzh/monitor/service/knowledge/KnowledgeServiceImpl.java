package com.lzzh.monitor.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.KnowledgeArticleRequest;
import com.lzzh.monitor.api.request.KnowledgePageRequest;
import com.lzzh.monitor.api.response.KnowledgeArticleVo;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.KnowledgeArticle;
import com.lzzh.monitor.dao.mapper.KnowledgeArticleMapper;
import com.lzzh.monitor.service.convert.KnowledgeConverter;
import com.lzzh.monitor.service.support.Pages;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeArticleMapper mapper;

    public KnowledgeServiceImpl(KnowledgeArticleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 分页查询知识文章。
     *
     * @param param 分页与分类过滤条件
     * @return 知识文章分页结果
     */
    @Override
    public PageResult<KnowledgeArticleVo> page(KnowledgePageRequest param) {
        KnowledgePageRequest req = param == null ? new KnowledgePageRequest() : param;
        Page<KnowledgeArticle> page = Pages.build(req);
        LambdaQueryWrapper<KnowledgeArticle> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getCategory()) && !"all".equalsIgnoreCase(req.getCategory())) {
            qw.eq(KnowledgeArticle::getCategory, req.getCategory());
        }
        if (StringUtils.hasText(req.getKeyword())) {
            String kw = req.getKeyword();
            qw.and(w -> w.like(KnowledgeArticle::getTitle, kw).or().like(KnowledgeArticle::getContent, kw));
        }
        qw.orderByDesc(KnowledgeArticle::getUpdateTime);
        return Pages.toResult(mapper.selectPage(page, qw)).map(KnowledgeConverter::toVo);
    }

    /**
     * 查询全部知识文章。
     *
     * @return 全部知识文章列表
     */
    @Override
    public List<KnowledgeArticleVo> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeArticle>()
                        .orderByDesc(KnowledgeArticle::getUpdateTime))
                .stream().map(KnowledgeConverter::toVo).toList();
    }

    /**
     * 查询文章详情，同时累加浏览次数。
     *
     * @param id 文章主键 ID
     * @return 知识文章详情，不存在时返回 null
     */
    @Override
    public KnowledgeArticleVo getById(Long id) {
        KnowledgeArticle e = mapper.selectById(id);
        if (e == null) {
            return null;
        }
        KnowledgeArticle inc = new KnowledgeArticle();
        inc.setId(id);
        inc.setViews((e.getViews() == null ? 0 : e.getViews()) + 1);
        mapper.updateById(inc);
        e.setViews(inc.getViews());
        return KnowledgeConverter.toVo(e);
    }

    /**
     * 新增知识文章。
     *
     * @param request 知识文章信息
     * @return 新建文章的主键 ID
     */
    @Override
    public Long create(KnowledgeArticleRequest request) {
        KnowledgeArticle e = KnowledgeConverter.toEntity(request);
        e.setViews(0);
        e.setLikes(0);
        e.setCreateTime(OffsetDateTime.now());
        e.setUpdateTime(OffsetDateTime.now());
        mapper.insert(e);
        return e.getId();
    }

    /**
     * 修改知识文章。
     *
     * @param request 知识文章信息（须含主键 ID）
     */
    @Override
    public void update(KnowledgeArticleRequest request) {
        KnowledgeArticle e = KnowledgeConverter.toEntity(request);
        e.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(e);
    }

    /**
     * 删除知识文章。
     *
     * @param id 文章主键 ID
     */
    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * 点赞，累加点赞数。
     *
     * @param id 文章主键 ID
     */
    @Override
    public void like(Long id) {
        KnowledgeArticle e = mapper.selectById(id);
        if (e == null) {
            return;
        }
        KnowledgeArticle inc = new KnowledgeArticle();
        inc.setId(id);
        inc.setLikes((e.getLikes() == null ? 0 : e.getLikes()) + 1);
        mapper.updateById(inc);
    }
}
