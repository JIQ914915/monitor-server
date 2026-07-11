package com.lzzh.monitor.admin.controller;

import com.lzzh.monitor.admin.security.RequiresPerm;
import com.lzzh.monitor.api.request.IdRequest;
import com.lzzh.monitor.api.request.KnowledgeArticleRequest;
import com.lzzh.monitor.api.request.KnowledgePageRequest;
import com.lzzh.monitor.api.response.KnowledgeArticleVo;
import com.lzzh.monitor.common.log.OperateLog;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.common.result.Result;
import com.lzzh.monitor.service.knowledge.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 知识库管理（全部角色可读，管理员/DBA 可写）。 */
@Tag(name = "知识库", description = "知识文章的分页查询、检索、增删改与点赞")
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgeService knowledgeService;

    /**
     * 分页查询知识文章。
     *
     * @param req 分页与分类过滤条件，可为空
     * @return 知识文章分页结果
     */
    @Operation(summary = "分页查询知识文章", description = "按关键字、分类分页检索知识文章")
    @PostMapping("/page")
    public Result<PageResult<KnowledgeArticleVo>> page(@Valid @RequestBody(required = false) KnowledgePageRequest req) {
        return Result.ok(knowledgeService.page(req == null ? new KnowledgePageRequest() : req));
    }

    /**
     * 查询全部知识文章。
     *
     * @return 全部知识文章列表（供分类计数、检索使用）
     */
    @Operation(summary = "查询全部知识文章", description = "返回全部知识文章，供分类计数与检索使用")
    @GetMapping("/list")
    public Result<List<KnowledgeArticleVo>> list() {
        return Result.ok(knowledgeService.listAll());
    }

    /**
     * 查询知识文章详情。
     *
     * @param req 主键入参
     * @return 知识文章详情（同时累加浏览次数）
     */
    @Operation(summary = "查询知识文章详情", description = "按主键 ID 查询文章详情，并累加浏览次数")
    @PostMapping("/get")
    public Result<KnowledgeArticleVo> detail(@Valid @RequestBody IdRequest req) {
        return Result.ok(knowledgeService.getById(req.getId()));
    }

    /**
     * 新增知识文章。
     *
     * @param req 知识文章信息
     * @return 新建文章的主键 ID
     */
    @Operation(summary = "新增知识文章", description = "创建一篇新的知识文章")
    @PostMapping
    @RequiresPerm("knowledge:create")
    @OperateLog(module = "知识库", action = "新增")
    public Result<Long> create(@Valid @RequestBody KnowledgeArticleRequest req) {
        return Result.ok(knowledgeService.create(req));
    }

    /**
     * 修改知识文章。
     *
     * @param req 知识文章信息（须含主键 ID）
     * @return 空响应体
     */
    @Operation(summary = "修改知识文章", description = "按主键更新知识文章")
    @PostMapping("/update")
    @RequiresPerm("knowledge:update")
    @OperateLog(module = "知识库", action = "修改")
    public Result<Void> update(@Valid @RequestBody KnowledgeArticleRequest req) {
        knowledgeService.update(req);
        return Result.ok();
    }

    /**
     * 删除知识文章。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "删除知识文章", description = "按主键删除知识文章")
    @PostMapping("/delete")
    @RequiresPerm("knowledge:delete")
    @OperateLog(module = "知识库", action = "删除")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        knowledgeService.delete(req.getId());
        return Result.ok();
    }

    /**
     * 点赞知识文章。
     *
     * @param req 主键入参
     * @return 空响应体
     */
    @Operation(summary = "点赞知识文章", description = "按主键为文章累加一次点赞")
    @PostMapping("/like")
    public Result<Void> like(@Valid @RequestBody IdRequest req) {
        knowledgeService.like(req.getId());
        return Result.ok();
    }
}
