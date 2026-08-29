package io.okagent.module.intent.application;

import java.util.List;

/** A node in the intent tree: the intent itself plus its resolved children. */
public record IntentNode(IntentDto node, List<IntentNode> children) {}
