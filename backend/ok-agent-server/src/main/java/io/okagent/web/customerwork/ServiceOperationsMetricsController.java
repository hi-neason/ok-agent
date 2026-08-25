package io.okagent.web.customerwork;

import io.okagent.service.customerwork.ServiceOperationsMetricsService;
import io.okagent.service.customerwork.ServiceOperationsMetricsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inbox/metrics")
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
