package com.zhaoyichi.devplatformbackend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 画像提示词构建器：集中管理 schema 与输出约束，便于迭代与论文附录展示。
 */
@Component
public class AiProfilePromptBuilder {

    public static final String PROFILE_VERSION = "v1";
    public static final String PROMPT_VERSION = "v1";

    @Autowired
    private ObjectMapper objectMapper;

    public List<Map<String, Object>> buildMessages(GithubRepoSampler.SampledGithubProfile sampled) {
        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(sampled);
        } catch (Exception e) {
            inputJson = "{}";
        }

        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(sys("你是一个用于“开发者协作平台”的分析助手。你的任务是基于输入的 GitHub 公开元数据与抽样文件，生成可解释的开发者画像。"
                + "必须遵守：1) 只基于输入，不要编造不存在的事实；2) 结论必须给出 evidence（repo+字段/文件）；3) 不能输出大量原文代码，最多摘要；"
                + "4) 不确定就写 uncertain=true；5) 只输出一个 JSON 对象，不要输出多余文字。"));

        msgs.add(user("请根据以下输入生成画像。输出 JSON schema 必须包含以下字段："
                + "{"
                + "\"profileVersion\":string,"
                + "\"promptVersion\":string,"
                + "\"githubUsername\":string,"
                + "\"techTags\":[{\"tag\":string,\"confidence\":number}],"
                + "\"topProjects\":[{\"repo\":string,\"reasons\":[string],\"techStack\":[string],\"signals\":[string]}],"
                + "\"deliverySignals\":[{\"signal\":string,\"level\":\"high\"|\"medium\"|\"low\",\"evidence\":[string]}],"
                + "\"codeQualityHeuristics\":[{\"item\":string,\"judgement\":string,\"uncertain\":boolean,\"evidence\":[string]}],"
                + "\"collaborationStyle\":{\"type\":string,\"notes\":string,\"evidence\":[string]},"
                + "\"risks\":[{\"risk\":string,\"severity\":\"high\"|\"medium\"|\"low\",\"evidence\":[string]}],"
                + "\"summary\":string,"
                + "\"evidence\":[{\"key\":string,\"detail\":string}]"
                + "}"
                + "其中 evidence 字段中的 key 建议格式：\"repo:<name>#file:<path>\" 或 \"user:<field>\" 或 \"repo:<name>#meta:<field>\"。"
                + "输入如下（JSON）：\n" + inputJson));
        return msgs;
    }

    /**
     * 当模型输出不是合法 JSON 时，用该提示让模型“只修 JSON”。\n
     */
    public List<Map<String, Object>> buildFixJsonMessages(String badOutput) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(sys("你将收到一个应该是 JSON 的输出，但它可能包含多余文本或语法错误。你的任务是：只返回修复后的 JSON 对象，确保可被严格解析。不要输出任何解释。"));
        msgs.add(user("待修复内容如下：\n" + (badOutput == null ? "" : badOutput)));
        return msgs;
    }

    private static Map<String, Object> sys(String s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "system");
        m.put("content", s);
        return m;
    }

    private static Map<String, Object> user(String s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "user");
        m.put("content", s);
        return m;
    }
}

