package com.lzzh.monitor.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.api.request.DrilldownProfileSaveRequest;
import com.lzzh.monitor.api.response.DrilldownProfileVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.AlertDrilldownProfile;
import com.lzzh.monitor.dao.mapper.AlertDrilldownProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 告警下钻画像服务实现（画像数 ≤ 20，全量加载内存匹配）。 */
@Service
public class AlertDrilldownProfileServiceImpl implements AlertDrilldownProfileService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Resource
    private AlertDrilldownProfileMapper profileMapper;

    @Override
    public List<DrilldownProfileVo> list() {
        return profileMapper.selectList(new LambdaQueryWrapper<AlertDrilldownProfile>()
                        .orderByAsc(AlertDrilldownProfile::getSort)
                        .orderByAsc(AlertDrilldownProfile::getId))
                .stream().map(this::toVo).toList();
    }

    @Override
    public Long save(DrilldownProfileSaveRequest req) {
        validateJsonBlocks(req);
        AlertDrilldownProfile existingByCode = profileMapper.selectOne(
                new LambdaQueryWrapper<AlertDrilldownProfile>()
                        .eq(AlertDrilldownProfile::getProfileCode, req.getProfileCode())
                        .last("LIMIT 1"));
        OffsetDateTime now = OffsetDateTime.now();
        if (req.getId() == null) {
            if (existingByCode != null) {
                throw new BusinessException("画像编码已存在：" + req.getProfileCode());
            }
            AlertDrilldownProfile p = new AlertDrilldownProfile();
            applyRequest(p, req);
            p.setBuiltin(false);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            profileMapper.insert(p);
            return p.getId();
        }
        AlertDrilldownProfile p = profileMapper.selectById(req.getId());
        if (p == null) {
            throw new BusinessException("画像不存在");
        }
        if (existingByCode != null && !Objects.equals(existingByCode.getId(), p.getId())) {
            throw new BusinessException("画像编码已存在：" + req.getProfileCode());
        }
        if (Boolean.TRUE.equals(p.getBuiltin()) && !Objects.equals(p.getProfileCode(), req.getProfileCode())) {
            throw new BusinessException("内置画像不允许修改编码");
        }
        applyRequest(p, req);
        p.setUpdatedAt(now);
        profileMapper.updateById(p);
        return p.getId();
    }

    @Override
    public void delete(Long id) {
        AlertDrilldownProfile p = profileMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("画像不存在");
        }
        if (Boolean.TRUE.equals(p.getBuiltin())) {
            throw new BusinessException("内置画像不可删除，可停用后自定义新画像覆盖");
        }
        profileMapper.deleteById(id);
    }

    @Override
    public void toggle(Long id, boolean enabled) {
        AlertDrilldownProfile p = profileMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("画像不存在");
        }
        p.setEnabled(enabled);
        p.setUpdatedAt(OffsetDateTime.now());
        profileMapper.updateById(p);
    }

    @Override
    public DrilldownProfileVo match(String metricCode, String dbType) {
        if (!StringUtils.hasText(dbType)) {
            return null;
        }
        List<AlertDrilldownProfile> enabled = profileMapper.selectList(
                new LambdaQueryWrapper<AlertDrilldownProfile>()
                        .eq(AlertDrilldownProfile::getEnabled, true)
                        .orderByAsc(AlertDrilldownProfile::getSort)
                        .orderByAsc(AlertDrilldownProfile::getId));
        if (enabled.isEmpty()) {
            return null;
        }
        enabled = enabled.stream()
                .filter(p -> dbType.equalsIgnoreCase(p.getDbType()))
                .toList();
        if (enabled.isEmpty()) {
            return null;
        }
        AlertDrilldownProfile hit = null;
        if (StringUtils.hasText(metricCode)) {
            // exact 优先
            hit = enabled.stream()
                    .filter(p -> rules(p).stream().anyMatch(r ->
                            "exact".equals(r.get("matchType")) && metricCode.equals(r.get("pattern"))))
                    .findFirst().orElse(null);
            if (hit == null) {
                // prefix 长者优先，避免 mysql.innodb. 抢先命中 buffer_pool 类
                record PrefixHit(AlertDrilldownProfile profile, int len) {}
                hit = enabled.stream()
                        .flatMap(p -> rules(p).stream()
                                .filter(r -> "prefix".equals(r.get("matchType"))
                                        && r.get("pattern") instanceof String s
                                        && !s.isBlank() && metricCode.startsWith(s))
                                .map(r -> new PrefixHit(p, ((String) r.get("pattern")).length())))
                        .max(Comparator.comparingInt(PrefixHit::len))
                        .map(PrefixHit::profile).orElse(null);
            }
        }
        if (hit == null) {
            // 兜底：match_rules 为空的画像（generic）
            hit = enabled.stream()
                    .filter(p -> rules(p).isEmpty())
                    .findFirst().orElse(null);
        }
        return hit == null ? null : toVo(hit);
    }

    private static List<Map<String, Object>> rules(AlertDrilldownProfile p) {
        return p.getMatchRules() == null ? List.of() : p.getMatchRules();
    }

    private static void applyRequest(AlertDrilldownProfile p, DrilldownProfileSaveRequest req) {
        p.setProfileCode(req.getProfileCode().trim());
        p.setProfileLabel(req.getProfileLabel().trim());
        p.setDbType(req.getDbType().trim().toLowerCase());
        p.setMatchRules(req.getMatchRules() == null ? List.of() : req.getMatchRules());
        p.setRelatedMetrics(req.getRelatedMetrics() == null ? List.of() : req.getRelatedMetrics());
        p.setCauses(req.getCauses() == null ? List.of() : req.getCauses());
        p.setSteps(req.getSteps() == null ? List.of() : req.getSteps());
        p.setActions(req.getActions() == null ? List.of() : req.getActions());
        p.setEnabled(req.getEnabled() == null || req.getEnabled());
        p.setSort(req.getSort() == null ? 0 : req.getSort());
        p.setRemark(req.getRemark());
    }

    /** 结构性校验：匹配规则字段必填、matchType 枚举合法，避免脏配置导致匹配异常。 */
    private static void validateJsonBlocks(DrilldownProfileSaveRequest req) {
        if (req.getMatchRules() != null) {
            for (Map<String, Object> r : req.getMatchRules()) {
                Object type = r.get("matchType");
                Object pattern = r.get("pattern");
                if (!"exact".equals(type) && !"prefix".equals(type)) {
                    throw new BusinessException("匹配规则 matchType 只支持 exact / prefix");
                }
                if (!(pattern instanceof String s) || s.isBlank()) {
                    throw new BusinessException("匹配规则 pattern 不能为空");
                }
            }
        }
    }

    private DrilldownProfileVo toVo(AlertDrilldownProfile p) {
        DrilldownProfileVo vo = new DrilldownProfileVo();
        vo.setId(p.getId());
        vo.setProfileCode(p.getProfileCode());
        vo.setProfileLabel(p.getProfileLabel());
        vo.setDbType(p.getDbType());
        vo.setMatchRules(rules(p));
        vo.setRelatedMetrics(p.getRelatedMetrics() == null ? List.of() : p.getRelatedMetrics());
        vo.setCauses(p.getCauses() == null ? List.of() : p.getCauses());
        vo.setSteps(p.getSteps() == null ? List.of() : p.getSteps());
        vo.setActions(p.getActions() == null ? List.of() : p.getActions());
        vo.setBuiltin(p.getBuiltin());
        vo.setEnabled(p.getEnabled());
        vo.setSort(p.getSort());
        vo.setRemark(p.getRemark());
        if (p.getCreatedAt() != null) {
            vo.setCreatedAt(p.getCreatedAt().format(FMT));
        }
        if (p.getUpdatedAt() != null) {
            vo.setUpdatedAt(p.getUpdatedAt().format(FMT));
        }
        return vo;
    }
}
