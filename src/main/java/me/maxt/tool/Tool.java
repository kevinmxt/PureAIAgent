package me.maxt.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface Tool {

    /** 工具名称，大模型通过此名称决定调用哪个工具 */
    String name();

    /** 工具描述，帮助大模型判断何时使用 */
    String description();

    /** 参数 JSON Schema */
    JsonNode parameters();

    /** 执行工具，参数为已解析的 JsonNode，返回执行结果文本 */
    String execute(JsonNode arguments) throws Exception;
}
