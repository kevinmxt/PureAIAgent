package me.maxt.command;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令分发器 — 持有已注册的 Command 列表，匹配用户输入并分发。
 * 同时负责从各 Command 的 name+description 自动生成启动横幅。
 */
public class CommandDispatcher {

    private final List<Command> commands = new ArrayList<>();

    public void register(Command cmd) {
        commands.add(cmd);
    }

    /**
     * 匹配并执行命令。
     * @return null 表示未匹配（应进入对话流程），
     *         true 表示已处理（继续循环），
     *         false 表示程序应退出
     */
    public Boolean dispatch(String input) {
        for (Command cmd : commands) {
            for (String alias : cmd.aliases().split(",")) {
                if (alias.trim().equals(input)) {
                    boolean shouldExit = cmd.handle(input);
                    return !shouldExit; // true=handled-continue, false=exit
                }
            }
        }
        return null; // not handled
    }

    /**
     * 自动生成启动横幅文本。
     */
    public String bannerText() {
        StringBuilder sb = new StringBuilder();
        for (Command cmd : commands) {
            sb.append("  输入 '").append(cmd.aliases()).append("' ").append(cmd.description());
            sb.append("\n");
        }
        return sb.toString();
    }
}
