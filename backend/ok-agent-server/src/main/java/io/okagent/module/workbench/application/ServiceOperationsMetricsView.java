package io.okagent.module.workbench.application;

/** MVP operational KPIs for the unified sales and support workbench. */
public record ServiceOperationsMetricsView(
        long totalConversations,
        long waitingHuman,
        long inProgress,
        long resolved,
        long leads,
        long tickets,
        long satisfactionResponses,
        Double averageSatisfaction) {}
