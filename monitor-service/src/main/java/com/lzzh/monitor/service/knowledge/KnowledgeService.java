package com.lzzh.monitor.service.knowledge;

import com.lzzh.monitor.api.request.KnowledgeArticleRequest;
import com.lzzh.monitor.api.request.KnowledgePageRequest;
import com.lzzh.monitor.api.response.KnowledgeArticleVo;
import com.lzzh.monitor.common.result.PageResult;

import java.util.List;

/** 知识库管理服务。 */
public interface KnowledgeService {

    /**
     * 分页查询知识文章。
     *
     * @param param 分页与分类过滤条件
     * @return 知识文章分页结果
     */
    PageResult<KnowledgeArticleVo> page(KnowledgePageRequest param);

    /**
     * 全部文章（供分类计数、检索）。
     *
     * @return 全部知识文章列表
     */
    List<KnowledgeArticleVo> listAll();

    /**
     * 文章详情（同时累加浏览次数）。
     *
     * @param id 文章主键 ID
     * @return 知识文章详情，不存在时返回 null
     */
    KnowledgeArticleVo getById(Long id);

    /**
     * 新增知识文章。
     *
     * @param request 知识文章信息
     * @return 新建文章的主键 ID
     */
    Long create(KnowledgeArticleRequest request);

    /**
     * 修改知识文章。
     *
     * @param request 知识文章信息（须含主键 ID）
     */
    void update(KnowledgeArticleRequest request);

    /**
     * 删除知识文章。
     *
     * @param id 文章主键 ID
     */
    void delete(Long id);

    /**
     * 点赞（累加点赞数）。
     *
     * @param id 文章主键 ID
     */
    void like(Long id);
}
