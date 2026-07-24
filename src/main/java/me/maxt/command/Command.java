package me.maxt.command;

/**
 * 特殊命令接口 — 用户输入的元命令（非对话内容）。
 * 每个命令自带名称、别名、描述，用于自动生成启动横幅和输入匹配。
 */
public interface Command {

    /** 命令显示名称，用于帮助文本 */
    String name();

    /** 触发别名，逗号分隔。如 "退出,exit" */
    String aliases();

    /** 一句话描述，用于自动生成启动横幅 */
    String description();

    /**
     * 处理命令。
     * @return true 表示程序应退出，false 表示继续运行
     */
    boolean handle(String input);
}
