package io.okagent.service.workflow;

import java.util.List;

/** Lightweight descriptor of a workflow exposed by a source, used for catalog listing. */
public record RemoteWorkflowSummary(
        String remoteWorkflowId,
        String name,
        boolean active,
        List<String> tags,
        String remoteDescription,
        String remoteMode) {}
