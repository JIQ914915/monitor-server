# Resource Injection and Mapper XML Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert production Spring dependencies to `@Resource` field injection and move every agreed long Mapper SQL statement to XML while retaining simple one-line SQL annotations.

**Architecture:** Establish executable source conventions first, then migrate Mapper SQL in small groups and Spring injection module by module. Preserve every public method signature and SQL behavior; each group is independently compiled, tested, and committed before the next group starts.

**Tech Stack:** Java 21, Spring Boot, `jakarta.annotation.Resource`, MyBatis-Plus, MyBatis XML, JUnit 5, Maven, PowerShell/rg.

---

## File Structure

- Create `monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/MapperXmlConventionTest.java`: detects long SQL annotations and validates XML namespaces and statement IDs.
- Create `monitor-dao/src/main/resources/mapper/*Mapper.xml`: one XML resource for each Mapper containing migrated long SQL.
- Modify `monitor-admin/src/main/resources/application.yml` and `monitor-collector/src/main/resources/application.yml`: explicitly load shared Mapper XML resources.
- Create `scripts/check-resource-injection.ps1`: checks production Spring classes for dependency constructors after migration.
- Modify production Spring classes in `monitor-service`, `monitor-admin`, `monitor-collector`, `monitor-collector-mysql`, `monitor-collector-postgresql`, and `monitor-collector-host`: use `@Resource` fields and remove dependency constructors.
- Modify tests that directly instantiate migrated Spring classes: construct with a no-arg constructor and inject mocks through `ReflectionTestUtils.setField`.

### Task 1: Add XML loading and failing Mapper convention coverage

**Files:**
- Create: `monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/MapperXmlConventionTest.java`
- Modify: `monitor-admin/src/main/resources/application.yml`
- Modify: `monitor-collector/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing convention test**

Create a JUnit test that scans `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper`, identifies mapping annotations whose source contains a text block, dynamic tag, multiple lines, or more than 120 SQL characters, and fails with the Mapper and method name. Add a second test that parses every `src/main/resources/mapper/*Mapper.xml`, requiring `namespace` to match an existing Mapper and every statement `id` to match an interface method.

```java
class MapperXmlConventionTest {
    private static final Path ROOT = Path.of(System.getProperty("maven.multiModuleProjectDirectory"));
    private static final Path MAPPER_JAVA = ROOT.resolve("monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper");
    private static final Path MAPPER_XML = ROOT.resolve("monitor-dao/src/main/resources/mapper");

    @Test
    void longSqlMustBeDefinedInXml() throws IOException {
        List<String> violations = MapperSourceConvention.findLongSqlAnnotations(MAPPER_JAVA);
        assertThat(violations).as("long Mapper SQL annotations").isEmpty();
    }

    @Test
    void mapperXmlNamespacesAndStatementIdsMatchInterfaces() throws Exception {
        List<String> violations = MapperXmlConvention.validate(MAPPER_XML, MAPPER_JAVA);
        assertThat(violations).isEmpty();
    }
}
```

Keep the scanner helpers package-private in the same test file. Parse Java source without adding dependencies: track `@Select/@Insert/@Update/@Delete` annotation blocks, count decoded SQL characters, and extract the following method name. Parse XML with a hardened `DocumentBuilderFactory` that disables external entities and DTD loading.

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-dao -am "-Dtest=MapperXmlConventionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL listing the current long SQL annotations.

- [ ] **Step 3: Configure both runtimes to load XML**

Add under each existing `mybatis-plus` block:

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 4: Verify YAML and commit the contract**

Run `git diff --check` and confirm each YAML file has one `mybatis-plus` block. Commit only the convention test and runtime configuration:

```powershell
git add monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/MapperXmlConventionTest.java monitor-admin/src/main/resources/application.yml monitor-collector/src/main/resources/application.yml
git commit -m "test: define mapper xml conventions"
```

### Task 2: Migrate alert, instance, and reporting SQL

**Files:**
- Modify: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/AlertEvaluateWindowMapper.java`
- Modify: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/AlertNotifyRecordMapper.java`
- Modify: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/CollectLogMapper.java`
- Modify: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/DbInstanceMapper.java`
- Modify: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapper.java`
- Modify: `monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper/ReportStatsMapper.java`
- Create: matching XML files under `monitor-dao/src/main/resources/mapper/`
- Modify: `monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapperTest.java`

- [ ] **Step 1: Add XML contract assertions before moving SQL**

Update `InstanceDataCleanupMapperTest` to read `mapper/InstanceDataCleanupMapper.xml` from the classpath and assert the same table coverage against XML text. Run it before creating the XML.

Expected: FAIL because the resource does not exist.

- [ ] **Step 2: Move SQL without changing signatures**

For each listed interface, remove only long mapping annotations and unused annotation imports. Create XML with this fixed header and matching namespace:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.lzzh.monitor.dao.mapper.InstanceDataCleanupMapper">
    <delete id="deleteByInstanceId">
        <!-- Copy the existing SQL body verbatim; replace annotation <script> wrapper with XML body. -->
    </delete>
</mapper>
```

Use `<select>`, `<insert>`, `<update>`, or `<delete>` according to the removed annotation. Escape SQL comparison operators as XML requires; preserve `${}` and `#{}` expressions exactly.

- [ ] **Step 3: Run focused and DAO tests**

Run:

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-dao -am test
```

Expected: mapper XML convention passes for this group; remaining long annotation violations are limited to the time-series Mapper group in Task 3.

- [ ] **Step 4: Commit the group**

```powershell
git add monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper monitor-dao/src/main/resources/mapper monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/InstanceDataCleanupMapperTest.java
git commit -m "refactor: move business mapper sql to xml"
```

### Task 3: Migrate time-series SQL and reach GREEN

**Files:**
- Modify these Mapper interfaces and create matching XML files:
  - `TsBaselineQueryMapper`
  - `TsCapacityObjectWriterMapper`
  - `TsLongConnWriterMapper`
  - `TsMetricLatestMapper`
  - `TsMetricObjectMapper`
  - `TsMetricWriterMapper`
  - `TsParamQueryMapper`
  - `TsSlowSqlSampleQueryMapper`
  - `TsSlowSqlSampleWriterMapper`
  - `TsTextReaderMapper`
  - `TsTextWriterMapper`
  - `TsTopSqlQueryMapper`
  - `TsTopSqlWriterMapper`
- Modify: `monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/TsTopSqlQueryMapperScriptTest.java`

- [ ] **Step 1: Convert the existing script test to XML-first behavior**

Load `mapper/TsTopSqlQueryMapper.xml`, build a `XMLMapperBuilder` with `MybatisConfiguration`, then obtain `MappedStatement` SQL for representative parameter maps. Retain all existing assertions about schema filters, ordering, limits, and parameterization.

- [ ] **Step 2: Verify the converted test fails**

Run only `TsTopSqlQueryMapperScriptTest`; expect missing XML resource or missing statement.

- [ ] **Step 3: Move every listed long SQL body**

Create one XML per Mapper. Keep Java methods and `@Param` annotations. For dynamic statements, convert annotation `<script>` contents directly into XML elements. Keep short one-line mapping annotations when they do not meet the agreed threshold.

- [ ] **Step 4: Verify Mapper migration GREEN**

Run:

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-dao -am test
rg -n '"""|<script>|<if>|<foreach>|<choose>' monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper -g '*.java'
```

Expected: DAO tests pass; `MapperXmlConventionTest.longSqlMustBeDefinedInXml` reports zero violations. Any remaining `rg` hit must be a documented short annotation false positive, otherwise migrate it before continuing.

- [ ] **Step 5: Commit the time-series group**

```powershell
git add monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper monitor-dao/src/main/resources/mapper monitor-dao/src/test/java/com/lzzh/monitor/dao/mapper/TsTopSqlQueryMapperScriptTest.java
git commit -m "refactor: move timeseries mapper sql to xml"
```

### Task 4: Establish the Resource injection contract

**Files:**
- Create: `scripts/check-resource-injection.ps1`

- [ ] **Step 1: Write the failing scanner**

The script must scan production `.java` files in the six modules, restrict checks to classes annotated with `@Service`, `@Component`, `@Controller`, `@RestController`, `@Configuration`, or `@Repository`, and report constructors that accept fields of component dependency types. Exclude constructors annotated `@Autowired(required = false)` only when they are not dependency injection constructors.

```powershell
$modules = @(
  'monitor-service', 'monitor-admin', 'monitor-collector',
  'monitor-collector-mysql', 'monitor-collector-postgresql', 'monitor-collector-host'
)
$violations = [System.Collections.Generic.List[string]]::new()
# Parse each Spring class, match its class-name constructor, and report constructors with parameters.
if ($violations.Count -gt 0) {
  $violations | ForEach-Object { Write-Error $_ }
  exit 1
}
```

Also report dependency-looking fields that are neither `final` constructor dependencies nor annotated with `@Resource`; this prevents incomplete conversions.

- [ ] **Step 2: Run and verify RED**

Run `pwsh -File scripts/check-resource-injection.ps1`.

Expected: non-zero exit with current Spring dependency constructors listed.

- [ ] **Step 3: Commit the contract script**

```powershell
git add scripts/check-resource-injection.ps1
git commit -m "test: define resource injection convention"
```

### Task 5: Convert monitor-service injection and tests

**Files:**
- Modify all annotated Spring classes reported by the contract under `monitor-service/src/main/java/com/lzzh/monitor/service/`.
- Modify affected tests under `monitor-service/src/test/java/`.

- [ ] **Step 1: Convert one service pattern at a time**

For every dependency constructor, apply this exact transformation:

```java
// before
private final DbInstanceMapper mapper;

public InstanceServiceImpl(DbInstanceMapper mapper) {
    this.mapper = mapper;
}

// after
@Resource
private DbInstanceMapper mapper;
```

Add `import jakarta.annotation.Resource;`. Preserve non-dependency constructors on ordinary helper/value classes. Preserve the user's current wildcard imports in `InstanceServiceImpl.java` while editing that file.

- [ ] **Step 2: Update direct-construction tests**

Replace constructor injection in tests with no-arg construction and Spring's test utility:

```java
service = new InstanceServiceImpl();
ReflectionTestUtils.setField(service, "mapper", dbInstanceMapper);
ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
ReflectionTestUtils.setField(service, "instanceDataCleanupMapper", cleanupMapper);
```

Inject every field the tested path uses. Do not weaken assertions or mock additional behavior unrelated to injection.

- [ ] **Step 3: Run service tests and scanner**

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-service -am test
pwsh -File scripts/check-resource-injection.ps1
```

Expected: service tests pass; scanner violations remain only in later modules.

- [ ] **Step 4: Commit service conversion**

```powershell
git add monitor-service scripts/check-resource-injection.ps1
git commit -m "refactor: use resource injection in services"
```

### Task 6: Convert monitor-admin injection

**Files:**
- Modify annotated Spring configuration, controllers, logging, and security classes under `monitor-admin/src/main/java` reported by the scanner.
- Modify affected tests under `monitor-admin/src/test/java`.

- [ ] **Step 1: Convert dependency constructors to Resource fields**

Use the same `@Resource private Type field;` pattern. For `SecurityConfig`, keep `@Bean` method parameters as method injection; only convert dependencies stored as fields. For multiple beans of one type, specify the existing bean name:

```java
@Resource(name = "authenticationManager")
private AuthenticationManager authenticationManager;
```

- [ ] **Step 2: Compile and test admin**

Run:

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-admin -am test
```

Expected: BUILD SUCCESS and no Spring context ambiguity.

- [ ] **Step 3: Commit admin conversion**

```powershell
git add monitor-admin
git commit -m "refactor: use resource injection in admin"
```

### Task 7: Convert collector core injection and tests

**Files:**
- Modify annotated Spring classes under `monitor-collector/src/main/java` reported by the scanner.
- Modify affected tests under `monitor-collector/src/test/java`, including `AlertEventLifecycleServiceTest`.

- [ ] **Step 1: Convert collector dependencies**

Use `@Resource` fields. Preserve constructor-created runtime values that are not beans. Where an interface has multiple implementations, inject by the bean name already declared with `@Component("...")` or `@Bean(name = "...")`.

- [ ] **Step 2: Update collector tests**

Use `ReflectionTestUtils.setField`. In `AlertEventLifecycleServiceTest`, inject all eight dependencies, including `blockingChainSnapshotService`; this also resolves the pre-existing stale constructor call without changing test behavior.

- [ ] **Step 3: Run collector tests**

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-collector -am test
```

Expected: BUILD SUCCESS with zero compilation errors, failures, or errors.

- [ ] **Step 4: Commit collector core conversion**

```powershell
git add monitor-collector
git commit -m "refactor: use resource injection in collector"
```

### Task 8: Convert collector plugin injection

**Files:**
- Modify annotated Spring classes reported by the scanner under:
  - `monitor-collector-mysql/src/main/java`
  - `monitor-collector-postgresql/src/main/java`
  - `monitor-collector-host/src/main/java`
- Modify corresponding module tests if present.

- [ ] **Step 1: Convert only Spring-managed plugin classes**

Do not convert collection item objects created manually by collectors unless the class itself is annotated as a Spring Bean. For each actual Spring Bean, replace dependency constructors with `@Resource` fields and retain constructors that initialize non-bean algorithm state.

- [ ] **Step 2: Run plugin tests and scanner**

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-collector-mysql,monitor-collector-postgresql,monitor-collector-host -am test
pwsh -File scripts/check-resource-injection.ps1
```

Expected: all module tests pass and the scanner exits 0.

- [ ] **Step 3: Commit plugin conversion**

```powershell
git add monitor-collector-mysql monitor-collector-postgresql monitor-collector-host
git commit -m "refactor: use resource injection in collector plugins"
```

### Task 9: Full verification and audit

**Files:**
- Modify only files with a verified convention gap found by the following checks.

- [ ] **Step 1: Run convention checks**

```powershell
pwsh -File scripts/check-resource-injection.ps1
rg -n '"""|<script>|<if>|<foreach>|<choose>' monitor-dao/src/main/java/com/lzzh/monitor/dao/mapper -g '*.java'
```

Expected: Resource scanner exits 0; no qualifying long SQL annotation remains.

- [ ] **Step 2: Run DAO and runtime module tests**

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' -pl monitor-dao,monitor-service,monitor-admin,monitor-collector -am test
```

Expected: BUILD SUCCESS with zero failures and errors.

- [ ] **Step 3: Run the full reactor**

```powershell
& 'D:\apache\apache-maven-3.9.16\bin\mvn.cmd' test
```

Expected: all ten reactor modules report SUCCESS.

- [ ] **Step 4: Audit final repository state**

```powershell
git diff origin/main --check
git status --short --branch
git log --oneline origin/main..HEAD
```

Expected: no whitespace errors; only the known user import change remains uncommitted if it was not naturally included in `InstanceServiceImpl` conversion; commits are separated by contract, Mapper groups, and injection modules.

- [ ] **Step 5: Commit any verified audit-only correction**

If and only if Steps 1-3 exposed a missed file, stage that exact file and commit:

```powershell
git commit -m "test: complete injection and mapper conventions"
```
