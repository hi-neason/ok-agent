package io.okagent.module.decision.application;

import io.okagent.module.agent.application.ResolvedAgentConfig;

/** Resolves a configured decision model and selects among frozen routing intents. */
public interface DecisionRoutingService {
    /** Returns a validated decision; a null key denotes an explicit no-match. */
    DecisionModelClient.Decision choose(ResolvedAgentConfig config, String query);
}
