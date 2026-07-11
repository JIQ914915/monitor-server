package com.lzzh.monitor.service.datascope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据范围解析结果：要么"全量可见"（{@link #isUnrestricted()}），
 * 要么是一个具体的可见实例 ID 集合（{@link #instanceIds()}）。
 *
 * <p>各业务查询/写操作统一依据本对象过滤，禁止在业务代码里各自实现数据范围逻辑，
 * 后续调整数据范围规则（如新增维度）只需改 {@link DataScopeService} 一处。
 */
public final class DataScope {

    private static final DataScope ALL = new DataScope(true, Collections.emptySet());

    private final boolean unrestricted;
    private final Set<Long> instanceIds;

    private DataScope(boolean unrestricted, Set<Long> instanceIds) {
        this.unrestricted = unrestricted;
        this.instanceIds = instanceIds;
    }

    public static DataScope all() {
        return ALL;
    }

    public static DataScope of(Set<Long> instanceIds) {
        return new DataScope(false, instanceIds == null ? Set.of() : new LinkedHashSet<>(instanceIds));
    }

    public boolean isUnrestricted() {
        return unrestricted;
    }

    /** 受限时的可见实例 ID 集合；全量可见时返回空集合（调用前应先判 {@link #isUnrestricted()}）。 */
    public Set<Long> instanceIds() {
        return instanceIds;
    }

    /** 受限且可见实例集合为空，即"无任何可见实例"——调用方应直接返回空结果，不必再查库。 */
    public boolean isEmpty() {
        return !unrestricted && instanceIds.isEmpty();
    }

    /** 判断某实例是否在当前数据范围内可见/可操作。 */
    public boolean allows(Long instanceId) {
        if (unrestricted) {
            return true;
        }
        return instanceId != null && instanceIds.contains(instanceId);
    }
}
