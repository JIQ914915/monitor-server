package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.AlertNotifyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 告警通知记录 Mapper。 */
@Mapper
public interface AlertNotifyRecordMapper extends BaseMapper<AlertNotifyRecord> {

    /**
     * 原子认领一批重试候选并置为 {@code sending}，多节点并发调用互不重复（SKIP LOCKED）。
     * <p>候选条件：
     * <ul>
     *   <li>{@code failed} 且未达最大重试次数、到达 next_retry_time；</li>
     *   <li>{@code pending} 且超过 2 分钟仍未发送（异步提交失败或进程崩溃的兜底）；</li>
     *   <li>{@code sending} 但超过 {@code staleSendingMinutes} 分钟未更新（认领方崩溃的兜底）。</li>
     * </ul>
     */
    List<AlertNotifyRecord> claimRetryCandidates(@Param("limit") int limit,
                                                 @Param("staleSendingMinutes") int staleSendingMinutes);

    /**
     * 异步首发前的单条认领：仅当记录仍为 {@code pending} 时置为 {@code sending}。
     * 返回 0 表示已被重试任务等其他执行方认领，调用方应跳过发送，避免双发。
     */
    int tryClaimPending(@Param("id") Long id);

    /**
     * 清理已到达终态且超过保留期的通知记录，防止表无限增长。
     * <p>终态定义：{@code success}、{@code dead}，或 {@code failed} 且已达最大重试次数（不再重试）。
     * 仍可能重试的 pending/sending/failed 记录不删除。
     *
     * @param retentionDays 保留天数
     * @param limit         单次删除上限，避免长事务
     * @return 实际删除行数
     */
    int deleteRetired(@Param("retentionDays") int retentionDays, @Param("limit") int limit);

    /**
     * 人工重发死信：仅当记录仍为 {@code dead} 时重置为 {@code failed}、清零重试次数并置 next_retry_time=now，
     * 使其重新进入 {@link #claimRetryCandidates} 的认领范围，由通知重试任务自动送达（复用既有发送/重试链路）。
     * 返回 0 表示记录不存在或已不是死信态（并发重发/已被其它操作改变），调用方据此判定是否成功。
     */
    int resetDeadForResend(@Param("id") Long id);
}
