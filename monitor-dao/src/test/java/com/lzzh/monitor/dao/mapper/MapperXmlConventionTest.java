package com.lzzh.monitor.dao.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperXmlConventionTest {

    private static final Pattern SQL_ANNOTATION = Pattern.compile("@(?:org\\.apache\\.ibatis\\.annotations\\.)?(Select|Insert|Update|Delete)\\s*\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\\\"(?:\\\\.|[^\\\"\\\\])*\\\"");
    private static final Pattern METHOD_NAME = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern DYNAMIC_TAG = Pattern.compile("(?i)<\\s*/?\\s*(script|if|choose|when|otherwise|foreach|where|set|trim|bind)\\b");
    private static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");
    private static final Set<String> SQL_ANNOTATION_NAMES = Set.of("Select", "Insert", "Update", "Delete");

    Path temporaryDirectory;

    @BeforeEach
    void createFixtureDirectory() throws IOException {
        temporaryDirectory = Files.createDirectories(moduleRoot().resolve("target/mapper-convention-fixtures")
                .resolve(UUID.randomUUID().toString()));
    }

    interface FixtureMapper {
        String customQuery();
    }

    @Test
    void longOrDynamicSqlMustLiveInMapperXml() throws Exception {
        List<String> violations = new ArrayList<>();
        Path mapperSourceDirectory = moduleRoot().resolve("src/main/java/com/lzzh/monitor/dao/mapper");
        try (Stream<Path> sources = Files.list(mapperSourceDirectory)) {
            for (Path source : sources.filter(path -> path.toString().endsWith("Mapper.java")).sorted().toList()) {
                inspectSqlAnnotations(source, violations);
            }
        }
        assertTrue(violations.isEmpty(), () -> "Long or dynamic SQL annotations must be moved to Mapper XML:\n"
                + String.join("\n", violations));
    }

    @Test
    void mapperXmlNamespacesAndStatementIdsMustMatchMapperMethods() throws Exception {
        List<String> violations = new ArrayList<>();
        Path root = moduleRoot();
        Path xmlDirectory = root.resolve("src/main/resources/mapper");
        Map<Class<?>, Set<String>> statements = inspectMapperXmlDirectory(xmlDirectory, violations);
        Path mapperSourceDirectory = root.resolve("src/main/java/com/lzzh/monitor/dao/mapper");
        try (Stream<Path> sources = Files.list(mapperSourceDirectory)) {
            for (Path source : sources.filter(path -> path.toString().endsWith("Mapper.java")).sorted().toList()) {
                String className = "com.lzzh.monitor.dao.mapper."
                        + source.getFileName().toString().replaceFirst("\\.java$", "");
                inspectRequiredXmlStatements(Class.forName(className), statements, violations);
            }
        }
        assertTrue(violations.isEmpty(), () -> "Invalid Mapper XML contract:\n" + String.join("\n", violations));
    }

    @Test
    void lexicalScannerIgnoresCommentsAndOrdinaryStrings() throws Exception {
        Path source = temporaryDirectory.resolve("LexicalFixtureMapper.java");
        Files.writeString(source, """
                interface LexicalFixtureMapper {
                    // @Select("THIS IS A COMMENT THAT MUST NEVER BE TREATED AS A SQL ANNOTATION ........................................................")
                    String value = "@Select(\\\"THIS IS AN ORDINARY STRING THAT MUST NEVER BE TREATED AS AN ANNOTATION ...................................\\\")";
                    @Select("SELECT this_is_a_real_annotation_with_a_body_that_is_deliberately_long_enough_to_exceed_the_one_hundred_and_twenty_character_limit FROM fixture")
                    String realLongQuery();
                    @org.apache.ibatis.annotations.Select("SELECT this_fully_qualified_annotation_is_also_deliberately_long_enough_to_exceed_the_one_hundred_and_twenty_character_limit FROM fixture")
                    String fullyQualifiedLongQuery();
                }
                """);
        List<String> violations = new ArrayList<>();
        inspectSqlAnnotations(source, violations);
        assertTrue(violations.size() == 2
                && violations.stream().anyMatch(value -> value.contains("#realLongQuery"))
                && violations.stream().anyMatch(value -> value.contains("#fullyQualifiedLongQuery")),
                violations::toString);
    }

    @Test
    void mapperXmlFixturesCoverBothContractDirections() throws Exception {
        Path valid = Files.createDirectory(temporaryDirectory.resolve("valid"));
        writeMapperXml(valid.resolve("anything.xml"), FixtureMapper.class.getName(), "customQuery");
        List<String> violations = new ArrayList<>();
        Map<Class<?>, Set<String>> statements = inspectMapperXmlDirectory(valid, violations);
        inspectRequiredXmlStatements(FixtureMapper.class, statements, violations);
        assertTrue(violations.isEmpty(), violations::toString);

        Path missing = Files.createDirectory(temporaryDirectory.resolve("missing"));
        violations.clear();
        statements = inspectMapperXmlDirectory(missing, violations);
        inspectRequiredXmlStatements(FixtureMapper.class, statements, violations);
        assertTrue(violations.stream().anyMatch(value -> value.contains("customQuery")), violations::toString);

        Path invalid = Files.createDirectory(temporaryDirectory.resolve("invalid"));
        writeMapperXml(invalid.resolve("bad-namespace.xml"), String.class.getName(), "length");
        writeMapperXml(invalid.resolve("bad-id.xml"), FixtureMapper.class.getName(), "missingMethod");
        violations.clear();
        inspectMapperXmlDirectory(invalid, violations);
        assertTrue(violations.stream().anyMatch(value -> value.contains("namespace must name an interface")), violations::toString);
        assertTrue(violations.stream().anyMatch(value -> value.contains("id has no Mapper method")), violations::toString);
    }

    @Test
    void missingMapperXmlDirectoryIsAContractViolation() {
        List<String> violations = new ArrayList<>();
        inspectMapperXmlDirectory(temporaryDirectory.resolve("absent"), violations);
        assertTrue(violations.stream().anyMatch(value -> value.contains("directory does not exist")), violations::toString);
    }

    private static void inspectSqlAnnotations(Path source, List<String> violations) throws IOException {
        String java = Files.readString(source, StandardCharsets.UTF_8);
        for (AnnotationBlock annotation : findSqlAnnotations(java)) {
            String annotationBody = java.substring(annotation.openParenthesis() + 1, annotation.closeParenthesis());
            String methodName = followingMethodName(java, annotation.closeParenthesis() + 1);
            String sql = extractSqlText(annotationBody);
            List<String> reasons = new ArrayList<>();
            if (annotationBody.contains("\"\"\"")) reasons.add("Java text block");
            if (annotationBody.indexOf('\n') >= 0 || annotationBody.indexOf('\r') >= 0) reasons.add("multiline annotation");
            if (DYNAMIC_TAG.matcher(sql).find()) reasons.add("dynamic SQL tag");
            if (sql.length() > 120) reasons.add("SQL body " + sql.length() + " chars");
            if (!reasons.isEmpty()) {
                violations.add(source.getFileName() + "#" + methodName + " [" + String.join(", ", reasons) + "]");
            }
        }
    }

    private static List<AnnotationBlock> findSqlAnnotations(String java) {
        List<AnnotationBlock> annotations = new ArrayList<>();
        LexicalState state = LexicalState.CODE;
        boolean escaped = false;
        for (int index = 0; index < java.length(); index++) {
            char current = java.charAt(index);
            if (state == LexicalState.LINE_COMMENT) {
                if (current == '\n' || current == '\r') state = LexicalState.CODE;
                continue;
            }
            if (state == LexicalState.BLOCK_COMMENT) {
                if (current == '*' && index + 1 < java.length() && java.charAt(index + 1) == '/') {
                    state = LexicalState.CODE;
                    index++;
                }
                continue;
            }
            if (state == LexicalState.TEXT_BLOCK) {
                if (java.startsWith("\"\"\"", index)) {
                    state = LexicalState.CODE;
                    index += 2;
                }
                continue;
            }
            if (state == LexicalState.STRING || state == LexicalState.CHARACTER) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (state == LexicalState.STRING && current == '"') state = LexicalState.CODE;
                else if (state == LexicalState.CHARACTER && current == '\'') state = LexicalState.CODE;
                continue;
            }
            if (current == '/' && index + 1 < java.length() && java.charAt(index + 1) == '/') {
                state = LexicalState.LINE_COMMENT;
                index++;
            } else if (current == '/' && index + 1 < java.length() && java.charAt(index + 1) == '*') {
                state = LexicalState.BLOCK_COMMENT;
                index++;
            } else if (java.startsWith("\"\"\"", index)) {
                state = LexicalState.TEXT_BLOCK;
                index += 2;
            } else if (current == '"') state = LexicalState.STRING;
            else if (current == '\'') state = LexicalState.CHARACTER;
            else if (current == '@') {
                Matcher matcher = SQL_ANNOTATION.matcher(java).region(index, java.length());
                if (matcher.lookingAt()) {
                    int open = matcher.end() - 1;
                    int close = matchingParenthesis(java, open);
                    if (close >= 0) {
                        annotations.add(new AnnotationBlock(open, close));
                        index = close;
                    }
                }
            }
        }
        return annotations;
    }

    private static int matchingParenthesis(String text, int open) {
        int depth = 0;
        LexicalState state = LexicalState.CODE;
        boolean escaped = false;
        for (int index = open; index < text.length(); index++) {
            char current = text.charAt(index);
            if (state == LexicalState.TEXT_BLOCK) {
                if (text.startsWith("\"\"\"", index)) { state = LexicalState.CODE; index += 2; }
                continue;
            }
            if (state == LexicalState.STRING || state == LexicalState.CHARACTER) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (state == LexicalState.STRING && current == '"') state = LexicalState.CODE;
                else if (state == LexicalState.CHARACTER && current == '\'') state = LexicalState.CODE;
                continue;
            }
            if (text.startsWith("\"\"\"", index)) { state = LexicalState.TEXT_BLOCK; index += 2; }
            else if (current == '"') state = LexicalState.STRING;
            else if (current == '\'') state = LexicalState.CHARACTER;
            else if (current == '(') depth++;
            else if (current == ')' && --depth == 0) return index;
        }
        return -1;
    }

    private static String followingMethodName(String java, int start) {
        int semicolon = java.indexOf(';', start);
        if (semicolon < 0) return "<unknown>";
        String declaration = java.substring(start, semicolon).replaceAll("@\\w+(?:\\([^)]*\\))?", "");
        Matcher names = METHOD_NAME.matcher(declaration);
        return names.find() ? names.group(1) : "<unknown>";
    }

    private static String extractSqlText(String annotationBody) {
        if (annotationBody.contains("\"\"\"")) {
            int start = annotationBody.indexOf("\"\"\"") + 3;
            int end = annotationBody.lastIndexOf("\"\"\"");
            return end >= start ? annotationBody.substring(start, end).stripIndent() : annotationBody;
        }
        StringBuilder sql = new StringBuilder();
        Matcher literals = STRING_LITERAL.matcher(annotationBody);
        while (literals.find()) {
            String literal = literals.group();
            sql.append(literal, 1, literal.length() - 1);
        }
        return sql.toString().replace("\\\"", "\"").replace("\\n", "\n");
    }

    private static Map<Class<?>, Set<String>> inspectMapperXmlDirectory(Path xmlDirectory, List<String> violations) {
        Map<Class<?>, Set<String>> statements = new LinkedHashMap<>();
        if (Files.notExists(xmlDirectory)) {
            violations.add(xmlDirectory + ": mapper XML directory does not exist");
            return statements;
        }
        try (Stream<Path> xmlFiles = Files.walk(xmlDirectory)) {
            for (Path xml : xmlFiles.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                inspectMapperXml(xml, statements, violations);
            }
        } catch (IOException exception) {
            violations.add(xmlDirectory + ": cannot scan mapper XML directory: " + exception.getMessage());
        }
        return statements;
    }

    private static void inspectMapperXml(Path xml, Map<Class<?>, Set<String>> statements, List<String> violations) {
        try {
            Document document = secureDocumentBuilderFactory().newDocumentBuilder().parse(xml.toFile());
            Element mapper = document.getDocumentElement();
            if (!"mapper".equals(mapper.getTagName())) {
                violations.add(xml.getFileName() + ": root element must be <mapper>");
                return;
            }
            String namespace = mapper.getAttribute("namespace");
            Class<?> mapperType;
            try {
                mapperType = Class.forName(namespace);
            } catch (ClassNotFoundException exception) {
                violations.add(xml.getFileName() + ": namespace does not name an existing Mapper: " + namespace);
                return;
            }
            if (!mapperType.isInterface() || !"com.lzzh.monitor.dao.mapper".equals(mapperType.getPackageName())) {
                violations.add(xml.getFileName() + ": namespace must name an interface in com.lzzh.monitor.dao.mapper: " + namespace);
                return;
            }
            Set<String> declaredMethods = new LinkedHashSet<>();
            Arrays.stream(mapperType.getDeclaredMethods()).map(Method::getName).forEach(declaredMethods::add);
            Set<String> ids = statements.computeIfAbsent(mapperType, ignored -> new LinkedHashSet<>());
            NodeList children = mapper.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                if (children.item(index) instanceof Element statement && STATEMENT_TAGS.contains(statement.getTagName())) {
                    String id = statement.getAttribute("id");
                    if (!declaredMethods.contains(id)) {
                        violations.add(xml.getFileName() + ": " + statement.getTagName() + " id has no Mapper method: " + id);
                    }
                    ids.add(id);
                }
            }
        } catch (Exception exception) {
            violations.add(xml.getFileName() + ": cannot be parsed safely: " + exception.getMessage());
        }
    }

    private static void inspectRequiredXmlStatements(Class<?> mapperType, Map<Class<?>, Set<String>> statements,
                                                      List<String> violations) {
        Set<String> ids = statements.getOrDefault(mapperType, Set.of());
        for (Method method : mapperType.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isAbstract(modifiers) || Modifier.isStatic(modifiers) || method.isDefault()
                    || hasSqlAnnotation(method)) {
                continue;
            }
            if (!ids.contains(method.getName())) {
                violations.add(mapperType.getName() + "#" + method.getName() + ": custom Mapper method has no XML statement");
            }
        }
    }

    private static boolean hasSqlAnnotation(Method method) {
        return Arrays.stream(method.getDeclaredAnnotations()).map(Annotation::annotationType)
                .map(Class::getSimpleName).anyMatch(SQL_ANNOTATION_NAMES::contains);
    }

    private static void writeMapperXml(Path xml, String namespace, String id) throws IOException {
        Files.writeString(xml, "<mapper namespace=\"" + namespace + "\"><select id=\"" + id + "\">SELECT 1</select></mapper>");
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory()
            throws ParserConfigurationException, SAXNotSupportedException, SAXNotRecognizedException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static Path moduleRoot() {
        String basedir = System.getProperty("project.basedir");
        Path workingDirectory = basedir == null
                ? Path.of("").toAbsolutePath().normalize()
                : Path.of(basedir).toAbsolutePath().normalize();
        if (Files.isDirectory(workingDirectory.resolve("src/main/java"))) return workingDirectory;
        if (Files.isDirectory(workingDirectory.resolve("monitor-dao/src/main/java"))) return workingDirectory.resolve("monitor-dao");
        throw new IllegalStateException("Cannot locate monitor-dao from " + workingDirectory);
    }

    private enum LexicalState { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHARACTER, TEXT_BLOCK }

    private record AnnotationBlock(int openParenthesis, int closeParenthesis) {}
}
