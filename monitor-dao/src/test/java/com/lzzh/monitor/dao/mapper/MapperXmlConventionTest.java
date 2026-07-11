package com.lzzh.monitor.dao.mapper;

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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperXmlConventionTest {

    private static final Pattern SQL_ANNOTATION = Pattern.compile("@(Select|Insert|Update|Delete)\\s*\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\\\"(?:\\\\.|[^\\\"\\\\])*\\\"");
    private static final Pattern METHOD_NAME = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern DYNAMIC_TAG = Pattern.compile("(?i)<\\s*/?\\s*(script|if|choose|when|otherwise|foreach|where|set|trim|bind)\\b");
    private static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");

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
        Path xmlDirectory = moduleRoot().resolve("src/main/resources/mapper");
        if (Files.notExists(xmlDirectory)) {
            return;
        }

        try (Stream<Path> xmlFiles = Files.walk(xmlDirectory)) {
            for (Path xml : xmlFiles.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                inspectMapperXml(xml, violations);
            }
        }

        assertTrue(violations.isEmpty(), () -> "Invalid Mapper XML contract:\n" + String.join("\n", violations));
    }

    private static void inspectSqlAnnotations(Path source, List<String> violations) throws IOException {
        String java = Files.readString(source, StandardCharsets.UTF_8);
        Matcher annotations = SQL_ANNOTATION.matcher(java);
        while (annotations.find()) {
            int openParenthesis = annotations.end() - 1;
            int closeParenthesis = matchingParenthesis(java, openParenthesis);
            if (closeParenthesis < 0) {
                violations.add(source.getFileName() + " (unterminated @" + annotations.group(1) + ")");
                continue;
            }
            String annotationBody = java.substring(openParenthesis + 1, closeParenthesis);
            String methodName = followingMethodName(java, closeParenthesis + 1);
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

    private static int matchingParenthesis(String text, int open) {
        int depth = 0;
        boolean inString = false;
        boolean inCharacter = false;
        boolean inTextBlock = false;
        boolean escaped = false;
        for (int index = open; index < text.length(); index++) {
            if (inTextBlock) {
                if (text.startsWith("\"\"\"", index)) {
                    inTextBlock = false;
                    index += 2;
                }
                continue;
            }
            char current = text.charAt(index);
            if (inString || inCharacter) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (inString && current == '"') inString = false;
                else if (inCharacter && current == '\'') inCharacter = false;
                continue;
            }
            if (text.startsWith("\"\"\"", index)) {
                inTextBlock = true;
                index += 2;
            } else if (current == '"') inString = true;
            else if (current == '\'') inCharacter = true;
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

    private static void inspectMapperXml(Path xml, List<String> violations) {
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
            if (!mapperType.isInterface()
                    || !"com.lzzh.monitor.dao.mapper".equals(mapperType.getPackageName())) {
                violations.add(xml.getFileName()
                        + ": namespace must name an interface in com.lzzh.monitor.dao.mapper: " + namespace);
                return;
            }
            Set<String> methods = new LinkedHashSet<>();
            Arrays.stream(mapperType.getMethods()).map(Method::getName).forEach(methods::add);
            NodeList children = mapper.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                if (children.item(index) instanceof Element statement && STATEMENT_TAGS.contains(statement.getTagName())) {
                    String id = statement.getAttribute("id");
                    if (!methods.contains(id)) {
                        violations.add(xml.getFileName() + ": " + statement.getTagName() + " id has no Mapper method: " + id);
                    }
                }
            }
        } catch (Exception exception) {
            violations.add(xml.getFileName() + ": cannot be parsed safely: " + exception.getMessage());
        }
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
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        return Files.isDirectory(workingDirectory.resolve("src/main/java"))
                ? workingDirectory
                : workingDirectory.resolve("monitor-dao");
    }
}
