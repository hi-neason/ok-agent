package io.okagent.module.workflow.application;

import java.util.List;

/** Remote metadata and input schema for one workflow, fetched from the source system. */
public record RemoteWorkflowDetail(
        String remoteWorkflowId,
        String name,
        String remoteMode,
        boolean active,
        List<String> tags,
        String remoteDescription,
        String inputSchemaJson,
        String rawJson) {}
