package net.corda.membership.lib.metrics

import io.micrometer.core.instrument.Timer
import net.corda.membership.lib.metrics.TimerMetricTypes.ACTIONS
import net.corda.membership.lib.metrics.TimerMetricTypes.REGISTRATION
import net.corda.membership.lib.metrics.TimerMetricTypes.SYNC
import net.corda.metrics.CordaMetrics

fun getTimerMetric(
    type: TimerMetricTypes,
    operation: String
): Timer {
    return when (type) {
        REGISTRATION -> CordaMetrics.Metric.MembershipRegistrationHandlerExecutionTime
        SYNC -> CordaMetrics.Metric.MembershipSynchronisationHandlerExecutionTime
        ACTIONS -> CordaMetrics.Metric.MembershipActionsHandlerExecutionTime
    }.builder()
        .withTag(CordaMetrics.Tag.OperationName, operation)
        .build()
}

enum class TimerMetricTypes {
    REGISTRATION, SYNC, ACTIONS;
}