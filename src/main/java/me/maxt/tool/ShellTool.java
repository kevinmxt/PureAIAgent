package me.maxt.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ShellTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "run_shell_command";
    }

    @Override
    public String description() {
        return "执行Windows命令行并返回输出。可以执行dir列出文件、type查看文件内容、echo输出文本等。执行耗时操作时建议加上超时控制。";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode cmd = props.putObject("command");
        cmd.put("type", "string");
        cmd.put("description", "要执行的cmd命令");
        ArrayNode required = schema.putArray("required");
        required.add("command");
        return schema;
    }

    @Override
    public String execute(JsonNode arguments) throws Exception {
        String command = arguments.get("command").asText();
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), "GBK");
        process.waitFor();
        return output.isBlank() ? "(命令执行成功，无输出)" : output.stripTrailing();
    }
}
