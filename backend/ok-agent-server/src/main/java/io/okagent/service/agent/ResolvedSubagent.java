package io.okagent.service.agent;

import java.util.List;

/**
 * A sub-agent reference resolved into the concrete config it will run as. For a draft this is the
 * child's current editable draft; for a release it is the child version snapshot pinned by the
 * parent version. Carries the declared {@code intentKeys} so the router's delegation description
 * can be enriched without re-reading the parent's raw JSON.
 */
public record ResolvedSubagent(ResolvedAgentConfig config, List<String> intentKeys) {}
