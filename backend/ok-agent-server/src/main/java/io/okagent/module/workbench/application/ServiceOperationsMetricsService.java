package io.okagent.module.workbench.application;

/** Aggregates operational KPIs for sales and customer-service supervisors. */
public interface ServiceOperationsMetricsService {
    /** Returns the current aggregate service operations metrics. */
    ServiceOperationsMetricsView get();
}
