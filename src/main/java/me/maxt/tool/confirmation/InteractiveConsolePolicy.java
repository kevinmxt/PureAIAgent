package me.maxt.tool.confirmation;

import me.maxt.model.ToolCall;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.util.HashSet;
import java.util.Set;

/**
 * 交互式控制台确认策略 — 打印 4 选项菜单，通过 LineReader 读取用户选择。
 * 当无终端环境时回退为 {@link AlwaysAllowPolicy} 的行为。
 */
public class InteractiveConsolePolicy implements ToolConfirmationPolicy {

    private final Terminal terminal;
    private final LineReader reader;
    private final Set<String> allowedActions = new HashSet<>();

    public InteractiveConsolePolicy(Terminal terminal, LineReader reader) {
        this.terminal = terminal;
        this.reader = reader;
    }

    @Override
    public ConfirmationDecision confirm(ToolCall tc) {
        String args = tc.getFunction().getArguments();

        if (allowedActions.contains(args)) {
            return ConfirmationDecision.ALLOW;
        }

        System.out.println("1.本次调用允许");
        System.out.println("2.本次会话均允许");
        System.out.println("3.本次调用不允许");
        System.out.println("4.结束本次会话");

        String choice;
        if (reader != null) {
            choice = reader.readLine("请选择: ");
        } else {
            choice = "1";
        }

        return switch (choice) {
            case "1" -> ConfirmationDecision.ALLOW;
            case "2" -> {
                allowedActions.add(args);
                yield ConfirmationDecision.ALLOW_ALWAYS;
            }
            case "3" -> ConfirmationDecision.DENY;
            case "4" -> ConfirmationDecision.STOP_SESSION;
            default -> {
                System.out.println("无效的选择:" + choice);
                yield ConfirmationDecision.ALLOW;
            }
        };
    }
}
