package com.erp.reporting.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Exposes the reconciliation health-check as the gauge {@code erp.reconciliation.healthy} (1 = the books
 * reconcile, 0 = they don't). Lives inside {@code reporting} so it uses {@link ReconciliationService}
 * directly (no module reaches reporting internals — boundaries stay clean).
 *
 * <p>Deliberately a metric, NOT a Spring {@code HealthIndicator}: a 0 reading must never flip
 * {@code /actuator/health}, or the Docker healthcheck would fail and the frontend's
 * {@code depends_on: service_healthy} would deadlock the demo. The value is computed on read but throttled
 * to at most once per minute, so a 15s Prometheus scrape never triggers a full DB reconciliation sweep.
 */
@Component
public class ReconciliationHealthGauge implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationHealthGauge.class);
    private static final long TTL_NANOS = 60_000_000_000L; // 60s

    private final ReconciliationService reconciliationService;
    private volatile double cached = Double.NaN;
    private volatile long lastComputedNanos;
    private volatile boolean computed;

    public ReconciliationHealthGauge(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("erp.reconciliation.healthy", this, ReconciliationHealthGauge::currentHealth)
                .description("1 if the ledger reconciles (trial balance balanced and every subledger "
                        + "equals its GL control account), else 0")
                .register(registry);
    }

    private synchronized double currentHealth() {
        long now = System.nanoTime();
        if (!computed || now - lastComputedNanos > TTL_NANOS) {
            try {
                cached = reconciliationService.reconcile(LocalDate.now()).healthy() ? 1.0 : 0.0;
            } catch (RuntimeException ex) {
                log.warn("reconciliation gauge: compute failed; keeping previous value", ex);
            }
            lastComputedNanos = now;
            computed = true;
        }
        return cached;
    }
}
