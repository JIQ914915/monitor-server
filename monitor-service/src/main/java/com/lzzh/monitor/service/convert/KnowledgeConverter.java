package com.lzzh.monitor.service.convert;

import com.lzzh.monitor.api.request.KnowledgeArticleRequest;
import com.lzzh.monitor.api.response.KnowledgeArticleVo;
import com.lzzh.monitor.dao.entity.KnowledgeArticle;

/** 知识文章实体 ↔ DTO 转换。 */
public final class KnowledgeConverter {

    private KnowledgeConverter() {
    }

    /**
     * 实体转响应 VO。
     *
     * @param e 知识文章实体
     * @return 知识文章响应 VO，入参为 null 时返回 null
     */
    public static KnowledgeArticleVo toVo(KnowledgeArticle e) {
        if (e == null) {
            return null;
        }
        KnowledgeArticleVo v = new KnowledgeArticleVo();
        v.setId(e.getId());
        v.setTitle(e.getTitle());
        v.setCategory(e.getCategory());
        v.setTags(e.getTags());
        v.setContent(e.getContent());
        v.setAuthor(e.getAuthor());
        v.setViews(e.getViews());
        v.setLikes(e.getLikes());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * 请求 DTO 转实体。
     *
     * @param r 知识文章请求 DTO
     * @return 知识文章实体，入参为 null 时返回 null
     */
    public static KnowledgeArticle toEntity(KnowledgeArticleRequest r) {
        if (r == null) {
            return null;
        }
        KnowledgeArticle e = new KnowledgeArticle();
        e.setId(r.getId());
        e.setTitle(r.getTitle());
        e.setCategory(r.getCategory());
        e.setTags(r.getTags());
        e.setContent(r.getContent());
        e.setAuthor(r.getAuthor());
        return e;
    }
}
