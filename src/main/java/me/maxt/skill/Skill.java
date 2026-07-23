package me.maxt.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SKILL 数据模型。从 SKILL.md 文件解析而来，包含 YAML frontmatter 元数据和正文内容。
 */
public class Skill {

    private final String name;
    private final String description;
    private final String content;

    public Skill(String name, String description, String content) {
        this.name = name;
        this.description = description;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }

    /**
     * 从 SKILL.md 文件解析 Skill。
     * 文件格式：YAML frontmatter（--- 分隔） + Markdown 正文。
     */
    public static Skill parse(Path skillMdPath) throws IOException {
        String text = Files.readString(skillMdPath, StandardCharsets.UTF_8);
        String[] parts = text.split("---", 3);

        if (parts.length < 2) {
            throw new IllegalArgumentException("SKILL.md 缺少 YAML frontmatter（需以 --- 开头）");
        }

        Map<String, String> frontmatter = parseFrontmatter(parts[1]);
        String name = frontmatter.get("name");
        String description = frontmatter.get("description");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少 name 字段");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少 description 字段");
        }

        String content = parts.length >= 3 ? parts[2].trim() : "";

        return new Skill(name.trim(), description.trim(), content);
    }

    private static Map<String, String> parseFrontmatter(String yaml) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : yaml.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                // 去除引号
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        return result;
    }
}
