package me.maxt.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SKILL 加载器。扫描指定目录下的子目录，解析 SKILL.md 文件。
 */
public class SkillLoader {

    private final Path skillDir;

    public SkillLoader(Path skillDir) {
        this.skillDir = skillDir;
    }

    /**
     * 扫描 skillDir 下的所有子目录，加载其中的 SKILL.md。
     * 解析异常会被捕获并打印警告，不影响其他 skill 的加载。
     */
    public List<Skill> load() {
        List<Skill> skills = new ArrayList<>();

        if (!Files.isDirectory(skillDir)) {
            return skills;
        }

        try (var entries = Files.newDirectoryStream(skillDir, Files::isDirectory)) {
            for (Path subDir : entries) {
                Path skillMd = subDir.resolve("SKILL.md");
                if (!Files.isRegularFile(skillMd)) {
                    continue;
                }
                try {
                    Skill skill = Skill.parse(skillMd);
                    skills.add(skill);
                    System.out.println("[SKILL] 已加载: " + skill.getName() + " (" + subDir.getFileName() + ")");
                } catch (IOException e) {
                    System.out.println("[SKILL] 读取失败: " + skillMd + " - " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.out.println("[SKILL] 解析失败: " + skillMd + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("[SKILL] 目录扫描失败: " + skillDir + " - " + e.getMessage());
        }

        return skills;
    }
}
