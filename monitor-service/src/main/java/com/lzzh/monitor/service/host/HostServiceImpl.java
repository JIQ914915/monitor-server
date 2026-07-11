package com.lzzh.monitor.service.host;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzzh.monitor.api.request.HostConnectionTestRequest;
import com.lzzh.monitor.api.request.HostPageRequest;
import com.lzzh.monitor.api.request.HostRequest;
import com.lzzh.monitor.api.response.HostCollectTargetVo;
import com.lzzh.monitor.api.response.HostOptionVo;
import com.lzzh.monitor.api.response.HostVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.result.PageResult;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.Host;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.HostMapper;
import com.lzzh.monitor.service.support.Pages;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HostServiceImpl implements HostService {

    private static final int DEFAULT_EXPORTER_PORT = 9100;
    private static final String DEFAULT_EXPORTER_PATH = "/metrics";
    private static final Pattern BUILD_INFO_VERSION =
            Pattern.compile("(node_exporter|windows_exporter)_build_info\\{[^}]*version=\"([^\"]+)\"");

    @Resource
    private HostMapper hostMapper;
    @Resource
    private DbInstanceMapper dbInstanceMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ── 查询 ──────────────────────────────────────────────────────────────────

    @Override
    public PageResult<HostVo> page(HostPageRequest query) {
        HostPageRequest q = query == null ? new HostPageRequest() : query;
        QueryWrapper<Host> qw = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            qw.and(w -> w.like("name", q.getKeyword()).or().like("ip", q.getKeyword()));
        }
        if (StringUtils.hasText(q.getStatus())) {
            qw.eq("status", q.getStatus());
        }
        if (StringUtils.hasText(q.getCollectMode())) {
            qw.eq("collect_mode", q.getCollectMode());
        }
        qw.orderByDesc("id");
        Page<Host> result = hostMapper.selectPage(Pages.build(q), qw);
        Map<Long, Integer> counts = countInstancesByHost(
                result.getRecords().stream().map(Host::getId).toList());
        return Pages.toResult(result).map(h -> toVo(h, counts.getOrDefault(h.getId(), 0)));
    }

    @Override
    public HostVo getById(Long id) {
        Host host = hostMapper.selectById(id);
        if (host == null) {
            throw new BusinessException("主机不存在: " + id);
        }
        Map<Long, Integer> counts = countInstancesByHost(List.of(id));
        return toVo(host, counts.getOrDefault(id, 0));
    }

    @Override
    public List<HostOptionVo> listOptions() {
        return hostMapper.selectList(new LambdaQueryWrapper<Host>().orderByDesc(Host::getId))
                .stream()
                .map(h -> {
                    HostOptionVo vo = new HostOptionVo();
                    vo.setId(h.getId());
                    vo.setName(h.getName());
                    vo.setIp(h.getIp());
                    vo.setOsType(h.getOsType());
                    return vo;
                })
                .toList();
    }

    /** 各主机关联实例数（列表展示与删除保护共用）。 */
    private Map<Long, Integer> countInstancesByHost(List<Long> hostIds) {
        if (hostIds == null || hostIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<DbInstance> qw = new QueryWrapper<DbInstance>()
                .select("host_id", "COUNT(*) AS cnt")
                .in("host_id", hostIds)
                .groupBy("host_id");
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : dbInstanceMapper.selectMaps(qw)) {
            if (row.get("host_id") instanceof Number id && row.get("cnt") instanceof Number cnt) {
                result.put(id.longValue(), cnt.intValue());
            }
        }
        return result;
    }

    // ── 写操作 ────────────────────────────────────────────────────────────────

    @Override
    public Long create(HostRequest request) {
        Host host = toEntity(request);
        host.setHostCode(UUID.randomUUID().toString());
        if (!StringUtils.hasText(host.getStatus())) {
            host.setStatus("normal");
        }
        applyDefaults(host);
        hostMapper.insert(host);
        return host.getId();
    }

    @Override
    public void update(HostRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("主机 ID 不能为空");
        }
        Host exists = hostMapper.selectById(request.getId());
        if (exists == null) {
            throw new BusinessException("主机不存在: " + request.getId());
        }
        Host host = toEntity(request);
        applyDefaults(host);
        hostMapper.updateById(host);
    }

    @Override
    public void delete(Long id) {
        Long count = dbInstanceMapper.selectCount(
                new LambdaQueryWrapper<DbInstance>().eq(DbInstance::getHostId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该主机仍关联 " + count + " 个实例，请先在实例管理中解除关联后再删除");
        }
        hostMapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id, String status) {
        if (!"normal".equals(status) && !"paused".equals(status)) {
            throw new BusinessException("非法状态：" + status + "（接口仅允许 normal/paused；abnormal 由采集器自动维护）");
        }
        Host host = hostMapper.selectById(id);
        if (host == null) {
            throw new BusinessException("主机不存在: " + id);
        }
        Host update = new Host();
        update.setId(id);
        update.setStatus(status);
        hostMapper.updateById(update);
    }

    private static void applyDefaults(Host host) {
        if (StringUtils.hasText(host.getOsType())
                && !"linux".equals(host.getOsType()) && !"windows".equals(host.getOsType())) {
            throw new BusinessException("非法操作系统类型：" + host.getOsType() + "（字典 host_os_type：linux/windows）");
        }
        if (!StringUtils.hasText(host.getCollectMode())) {
            host.setCollectMode("exporter");
        }
        if (host.getExporterPort() == null) {
            host.setExporterPort(DEFAULT_EXPORTER_PORT);
        }
        if (!StringUtils.hasText(host.getExporterPath())) {
            host.setExporterPath(DEFAULT_EXPORTER_PATH);
        }
    }

    // ── 连通性测试 ────────────────────────────────────────────────────────────

    @Override
    public String testConnection(HostConnectionTestRequest req) {
        int port = req.getExporterPort() == null ? DEFAULT_EXPORTER_PORT : req.getExporterPort();
        String path = StringUtils.hasText(req.getExporterPath()) ? req.getExporterPath() : DEFAULT_EXPORTER_PATH;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String url = "http://" + req.getIp() + ":" + port + path;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException("连接失败：exporter 返回 HTTP " + response.statusCode()
                        + "，请确认地址与端口配置");
            }
            String body = response.body() == null ? "" : response.body();
            long sampleLines = body.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).count();
            if (sampleLines == 0) {
                throw new BusinessException("连接成功但未解析到任何指标，请确认该地址是 node_exporter / windows_exporter 的 /metrics 端点");
            }
            // 校验是否为受支持的主机 exporter：普通 HTTP 服务/其它 exporter 虽可达但采集器无法产出主机指标，
            // 提前在测试环节报出，而不是等采集任务落一堆失败日志
            boolean isLinuxExporter = body.lines().anyMatch(l -> l.startsWith("node_"));
            boolean isWindowsExporter = body.lines().anyMatch(l -> l.startsWith("windows_"));
            if (!isLinuxExporter && !isWindowsExporter) {
                throw new BusinessException("端点可达但不是主机 exporter（未发现 node_* / windows_* 指标），"
                        + "请确认部署的是 node_exporter（Linux）或 windows_exporter（Windows）");
            }
            // exporter 类型与登记的操作系统类型交叉校验，避免选错类型导致展示口径不符
            if ("linux".equals(req.getOsType()) && !isLinuxExporter) {
                throw new BusinessException("类型不匹配：主机登记为 Linux，但端点是 windows_exporter，"
                        + "请将操作系统类型改为 Windows 或确认 exporter 部署");
            }
            if ("windows".equals(req.getOsType()) && !isWindowsExporter) {
                throw new BusinessException("类型不匹配：主机登记为 Windows，但端点是 node_exporter，"
                        + "请将操作系统类型改为 Linux 或确认 exporter 部署");
            }
            Matcher m = BUILD_INFO_VERSION.matcher(body);
            String exporterDesc = m.find() ? m.group(1) + " " + m.group(2)
                    : (isWindowsExporter ? "windows_exporter" : "node_exporter") + " 可达";
            return exporterDesc + "，可解析指标 " + sampleLines + " 行";
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("连接失败：" + rootMessage(e) + "（请检查主机网络与 exporter 进程、防火墙放行 " + port + " 端口）");
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    // ── 采集分片 ──────────────────────────────────────────────────────────────

    @Override
    public List<HostCollectTargetVo> listByShard(int shardIndex, int shardTotal) {
        List<Host> shard = hostMapper.selectByShard(shardIndex, shardTotal);
        if (shard.isEmpty()) {
            return List.of();
        }
        // 主机 → 关联实例 ID 列表（扇出写入目标；paused 实例不作为扇出目标）
        List<DbInstance> related = dbInstanceMapper.selectList(
                new LambdaQueryWrapper<DbInstance>()
                        .select(DbInstance::getId, DbInstance::getHostId, DbInstance::getStatus)
                        .in(DbInstance::getHostId, shard.stream().map(Host::getId).toList()));
        Map<Long, List<Long>> instancesByHost = new HashMap<>();
        for (DbInstance ins : related) {
            if ("paused".equals(ins.getStatus())) {
                continue;
            }
            instancesByHost.computeIfAbsent(ins.getHostId(), k -> new java.util.ArrayList<>()).add(ins.getId());
        }
        return shard.stream().map(h -> {
            HostCollectTargetVo t = new HostCollectTargetVo();
            t.setId(h.getId());
            t.setHostCode(h.getHostCode());
            t.setName(h.getName());
            t.setIp(h.getIp());
            t.setCollectMode(h.getCollectMode());
            t.setExporterPort(h.getExporterPort());
            t.setExporterPath(h.getExporterPath());
            t.setStatus(h.getStatus());
            t.setInstanceIds(instancesByHost.getOrDefault(h.getId(), List.of()));
            return t;
        }).toList();
    }

    // ── 转换 ──────────────────────────────────────────────────────────────────

    private static HostVo toVo(Host h, int instanceCount) {
        HostVo vo = new HostVo();
        vo.setId(h.getId());
        vo.setHostCode(h.getHostCode());
        vo.setName(h.getName());
        vo.setIp(h.getIp());
        vo.setOsType(h.getOsType());
        vo.setCollectMode(h.getCollectMode());
        vo.setExporterPort(h.getExporterPort());
        vo.setExporterPath(h.getExporterPath());
        vo.setRemark(h.getRemark());
        vo.setStatus(h.getStatus());
        vo.setInstanceCount(instanceCount);
        vo.setCreateTime(h.getCreateTime());
        vo.setUpdateTime(h.getUpdateTime());
        return vo;
    }

    private static Host toEntity(HostRequest r) {
        Host h = new Host();
        h.setId(r.getId());
        h.setName(r.getName());
        h.setIp(r.getIp());
        h.setOsType(r.getOsType());
        h.setCollectMode(r.getCollectMode());
        h.setExporterPort(r.getExporterPort());
        h.setExporterPath(r.getExporterPath());
        h.setRemark(r.getRemark());
        h.setStatus(r.getStatus());
        return h;
    }
}
