package io.okagent.module.agent.application;

import io.okagent.module.decision.application.DecisionModelClient;
import io.okagent.module.intent.application.IntentClassification;

/** Shared confidence and delegation policy for web pre-classification and Harness middleware. */
public final class DecisionRoutingPolicy {
    public static final double MIN_CONFIDENCE = 0.6;

    private DecisionRoutingPolicy() {}

    public static IntentClassification classify(ResolvedAgentConfig config, DecisionModelClient.Decision decision) {
        var matched = config.getResolvedIntents().stream()
                .filter(intent -> intent.intentKey().equals(decision.key()))
                .findFirst()
                .orElse(null);
        String delegate = null;
        if (matched != null && (decision.confidence() == null || decision.confidence() >= MIN_CONFIDENCE)) {
            delegate = config.getSubagents().stream()
                    .filter(sub -> sub.intentKeys() != null && sub.intentKeys().contains(matched.intentKey()))
                    .map(sub -> sub.config().getAgentKey())
                    .filter(key -> key != null && !key.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return new IntentClassification(
                matched == null ? null : matched.intentKey(),
                matched == null ? null : matched.name(),
                decision.confidence(),
                delegate,
                delegate == null);
    }

    public static String instruction(IntentClassification classification) {
        if (classification.fallback() || classification.targetSubagentKey() == null) return null;
        return "[路由指令] 用户意图已确定为「" + classification.intentName() + "」(意图键: "
                + classification.intentKey() + ")。立即调用 agent_spawn 工具，将 agent_id 设为「"
                + classification.targetSubagentKey()
                + "」、task 设为用户原问题，将任务委派给该子Agent处理；等待返回后将回复转达给用户。";
    }
}
