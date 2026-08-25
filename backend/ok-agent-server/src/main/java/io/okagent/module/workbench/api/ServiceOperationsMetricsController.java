package io.okagent.module.workbench.api;

import io.okagent.module.workbench.application.ServiceOperationsMetricsService;
import io.okagent.module.workbench.application.ServiceOperationsMetricsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/workbench/metrics", "/api/v1/inbox/metrics"})
public class ServiceOperationsMetricsController {
    private final ServiceOperationsMetricsService metrics;

    public ServiceOperationsMetricsController(ServiceOperationsMetricsService metrics) {
        this.metrics = metrics;
    }

    /** Returns all-time operational KPIs for the MVP sales and support workbench. */
    @GetMapping
    public ServiceOperationsMetricsView get() {
        return metrics.get();
    }
}
