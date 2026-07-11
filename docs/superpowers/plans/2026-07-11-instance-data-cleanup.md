# Instance Data Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deleting a database instance atomically removes all of its collection data, instance-scoped configuration, alert events, and alert audit children before removing the instance record.

**Architecture:** Add a focused MyBatis mapper whose single public operation performs ordered, parameterized deletes for one `instance_id`. Keep orchestration in `InstanceServiceImpl.delete`, which verifies existence and wraps cleanup plus the final instance delete in one Spring transaction.

**Tech Stack:** Java 21, Spring Boot, Spring transactions, MyBatis annotations, MyBatis-Plus, PostgreSQL/TimescaleDB, JUnit 5, Mockito, Maven.

---

## File Structure

- Create `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapper.java`: owns ordered SQL cleanup for one instance.
- Create `monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapperTest.java`: verifies the mapper contract and SQL table coverage without a live database.
- Create `monitor-service/src/test/java/com/lzzh/monitor/service/instance/InstanceServiceImplTest.java`: verifies delete orchestration and missing-instance behavior.
- Modify `monitor-service/src/main/java/com/lzzh/monitor/service/instance/InstanceServiceImpl.java`: inject cleanup mapper, verify existence, add transaction, clean children before deleting the instance.

### Task 1: Define and test the cleanup SQL contract

**Files:**
- Create: `monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapperTest.java`
- Create: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapper.java`

- [ ] **Step 1: Write the failing mapper contract test**

Create a reflection-based test that requires a `deleteByInstanceId(Long)` method annotated with MyBatis `@Delete`. Read the annotation value and assert that the SQL contains deletes for alert child tables, alert events, instance configuration/state tables, and every current collection table:

```java
@Test
void cleanupSqlCoversAllInstanceOwnedTables() throws Exception {
    Method method = InstanceDataCleanupMapper.class
            .getMethod("deleteByInstanceId", Long.class);
    Delete annotation = method.getAnnotation(Delete.class);
    String sql = String.join("\n", annotation.value()).toLowerCase();

    assertThat(sql).contains(
            "delete from alert_notify_record",
            "delete from alert_event_operate_log",
            "delete from llm_analysis",
            "delete from alert_event",
            "delete from alert_rule_instance_config",
            "delete from scenario_instance_config",
            "delete from alert_evaluate_lock",
            "delete from alert_evaluate_window",
            "delete from instance_collector",
            "delete from collect_log",
            "delete from counter_snapshot",
            "delete from slow_sql_optimize_mark",
            "delete from metric_data_1m",
            "delete from metric_data_1h",
            "delete from metric_data_1d",
            "delete from metric_text_data_1m",
            "delete from metric_text_data_1h",
            "delete from metric_text_data_1d",
            "delete from metric_top_sql",
            "delete from metric_capacity_object",
            "delete from metric_long_conn",
            "delete from metric_slow_sql_sample");
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `mvn -pl monitor-dao -am -Dtest=InstanceDataCleanupMapperTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because `InstanceDataCleanupMapper` does not exist.

- [ ] **Step 3: Implement the mapper with ordered statements**

Create an `@Mapper` interface with one `@Delete` method. Use a MyBatis `<script>` block containing semicolon-separated parameterized deletes. Event children must use `event_id IN (SELECT id FROM alert_event WHERE instance_id = #{instanceId})` before deleting `alert_event`. Delete evaluation windows by matching dedup keys whose parsed instance segment equals the target ID, then delete direct `instance_id` rows from configuration, state, and collection tables.

```java
@Mapper
public interface InstanceDataCleanupMapper {
    @Delete("""
            <script>
            DELETE FROM alert_notify_record
             WHERE event_id IN (SELECT id FROM alert_event WHERE instance_id = #{instanceId});
            DELETE FROM alert_event_operate_log
             WHERE event_id IN (SELECT id FROM alert_event WHERE instance_id = #{instanceId});
            DELETE FROM llm_analysis
             WHERE event_id IN (SELECT id FROM alert_event WHERE instance_id = #{instanceId});
            DELETE FROM alert_event WHERE instance_id = #{instanceId};
            <!-- remaining explicit instance-owned table deletes -->
            </script>
            """)
    int deleteByInstanceId(@Param("instanceId") Long instanceId);
}
```

The final implementation must spell out every table asserted by the test; no dynamic table names or string interpolation are allowed.

- [ ] **Step 4: Run the mapper test and verify GREEN**

Run: `mvn -pl monitor-dao -am -Dtest=InstanceDataCleanupMapperTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `BUILD SUCCESS`, one mapper contract test passing.

- [ ] **Step 5: Commit the mapper contract**

```bash
git add monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapper.java monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapperTest.java
git commit -m "feat: add instance data cleanup mapper"
```

### Task 2: Orchestrate atomic instance deletion

**Files:**
- Create: `monitor-service/src/test/java/com/lzzh/monitor/service/instance/InstanceServiceImplTest.java`
- Modify: `monitor-service/src/main/java/com/lzzh/monitor/service/instance/InstanceServiceImpl.java`

- [ ] **Step 1: Write failing service tests**

Construct `InstanceServiceImpl` with Mockito mocks. Stub an unrestricted `DataScope`, then verify the desired behavior:

```java
@Test
void deleteCleansAssociatedDataBeforeDeletingInstance() {
    DbInstance instance = new DbInstance();
    instance.setId(7L);
    when(dataScopeService.currentScope()).thenReturn(DataScope.unrestricted());
    when(dbInstanceMapper.selectById(7L)).thenReturn(instance);

    service.delete(7L);

    InOrder order = inOrder(cleanupMapper, dbInstanceMapper);
    order.verify(cleanupMapper).deleteByInstanceId(7L);
    order.verify(dbInstanceMapper).deleteById(7L);
}

@Test
void deleteRejectsMissingInstanceWithoutCleaningData() {
    when(dataScopeService.currentScope()).thenReturn(DataScope.unrestricted());
    when(dbInstanceMapper.selectById(7L)).thenReturn(null);

    assertThatThrownBy(() -> service.delete(7L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("实例不存在: 7");
    verifyNoInteractions(cleanupMapper);
    verify(dbInstanceMapper, never()).deleteById(any());
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `mvn -pl monitor-service -am -Dtest=InstanceServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation or assertion failure because the service has no cleanup dependency and does not check instance existence during deletion.

- [ ] **Step 3: Implement minimal transactional orchestration**

Inject `InstanceDataCleanupMapper`, annotate the public method with `@Transactional`, and update only the delete path:

```java
@Override
@Transactional
public void delete(Long id) {
    checkAccessible(id);
    if (mapper.selectById(id) == null) {
        throw new BusinessException("实例不存在: " + id);
    }
    instanceDataCleanupMapper.deleteByInstanceId(id);
    mapper.deleteById(id);
}
```

The transaction must use the default rollback behavior so database exceptions roll back cleanup and the main-row delete together.

- [ ] **Step 4: Run the focused service tests and verify GREEN**

Run: `mvn -pl monitor-service -am -Dtest=InstanceServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `BUILD SUCCESS`, both delete behavior tests passing.

- [ ] **Step 5: Commit the service behavior**

```bash
git add monitor-service/src/main/java/com/lzzh/monitor/service/instance/InstanceServiceImpl.java monitor-service/src/test/java/com/lzzh/monitor/service/instance/InstanceServiceImplTest.java
git commit -m "fix: remove instance-owned data on delete"
```

### Task 3: Verify schema coverage and regression safety

**Files:**
- Modify if coverage gaps are found: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapper.java`
- Modify if coverage gaps are found: `monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapperTest.java`

- [ ] **Step 1: Audit Flyway migrations for instance-owned tables**

Run: `rg -n -B 8 -A 8 "instance_id" monitor-admin/src/main/resources/db/migration -g "*.sql"`

Expected: every physical table containing a scalar `instance_id` is either explicitly cleaned or documented as a derived view/continuous aggregate. JSON array fields such as report snapshots are reviewed separately and are not mistaken for scalar foreign keys.

- [ ] **Step 2: Run DAO and service module tests**

Run: `mvn -pl monitor-dao,monitor-service -am test`

Expected: `BUILD SUCCESS` with zero test failures.

- [ ] **Step 3: Run the full server verification**

Run: `mvn test`

Expected: reactor `BUILD SUCCESS` with zero failures and zero errors.

- [ ] **Step 4: Review the final diff against the design**

Run: `git diff HEAD~2 --check` and `git diff HEAD~2 --stat`

Expected: no whitespace errors; changes are limited to the cleanup mapper, its contract test, instance service, its behavior test, and planning documentation.

- [ ] **Step 5: Commit any audit correction**

If Step 1 revealed a missing physical table, add its explicit delete and assertion, rerun Steps 2-4, then commit:

```bash
git add monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapper.java monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapperTest.java
git commit -m "test: complete instance cleanup coverage"
```
