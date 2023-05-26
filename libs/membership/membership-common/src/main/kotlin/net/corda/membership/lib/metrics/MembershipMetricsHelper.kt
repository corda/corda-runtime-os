package net.corda.membership.lib.metrics

import io.micrometer.core.instrument.Timer
import net.corda.membership.lib.metrics.SettableGaugeMetricTypes.MEMBER_LIST
import net.corda.membership.lib.metrics.TimerMetricTypes.ACTIONS
import net.corda.membership.lib.metrics.TimerMetricTypes.REGISTRATION
import net.corda.membership.lib.metrics.TimerMetricTypes.SYNC
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.Metric.Membership
import net.corda.metrics.SettableGauge
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda

enum class TimerMetricTypes {
    REGISTRATION, SYNC, ACTIONS;
}

fun getTimerMetric(
    type: TimerMetricTypes,
    holdingId: net.corda.data.identity.HoldingIdentity?,
    operation: String
): Timer {
    val builder = when (type) {
        REGISTRATION -> Membership.RegistrationHandlerExecutionTime
        SYNC -> Membership.SyncHandlerExecutionTime
        ACTIONS -> Membership.ActionsHandlerExecutionTime
    }.builder().withTag(CordaMetrics.Tag.OperationName, operation)
    holdingId?.let {
        builder.forVirtualNode(it.toCorda().shortHash.value)
            .withTag(CordaMetrics.Tag.MembershipGroup, it.groupId)
    }
    return builder.build()
}

enum class SettableGaugeMetricTypes {
    MEMBER_LIST;
}

fun getSettableGaugeMetric(
    type: SettableGaugeMetricTypes,
    holdingId: HoldingIdentity
): SettableGauge {
    return when(type) {
        MEMBER_LIST -> Membership.MemberListCacheSize
    }.builder()
        .forVirtualNode(holdingId.shortHash.value)
        .withTag(CordaMetrics.Tag.MembershipGroup, holdingId.groupId)
        .build()
}
