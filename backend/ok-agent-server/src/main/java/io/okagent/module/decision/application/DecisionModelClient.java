package io.okagent.module.decision.application;

import java.time.Duration;
import java.util.List;

/** Provider-neutral SDK for a bounded semantic choice; credentials are call-local only. */
public interface DecisionModelClient {
    /** Returns whether this client implements the configured provider. */
    boolean supports(String provider);

    /** Selects a candidate key; a null key denotes an explicit no-match decision. */
    Decision choose(
            String endpoint, String modelId, String apiKey, String query, List<Candidate> candidates, Duration timeout);

    /** A bounded decision with optional provider confidence. */
    record Decision(String key, Double confidence) {
        public Decision {
            if (confidence != null && (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)) {
                throw new IllegalArgumentException("Decision confidence must be between zero and one");
            }
        }
    }

    record Candidate(String key, String name, String description) {}
}
