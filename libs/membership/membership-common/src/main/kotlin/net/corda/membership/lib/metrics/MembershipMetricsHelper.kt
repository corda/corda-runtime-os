package net.corda.membership.lib.metrics

import io.micrometer.core.instrument.Timer
import net.corda.membership.lib.metrics.SettableGaugeMetricTypes.MEMBER_LIST
import net.corda.membership.lib.metrics.TimerMetricTypes.ACTIONS
import net.corda.membership.lib.metrics.TimerMetricTypes.PERSISTENCE_HANDLER
import net.corda.membership.lib.metrics.TimerMetricTypes.PERSISTENCE_TRANSACTION
import net.corda.membership.lib.metrics.TimerMetricTypes.REGISTRATION
import net.corda.membership.lib.metrics.TimerMetricTypes.SYNC
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.Metric.Membership
import net.corda.metrics.SettableGauge
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda

enum class TimerMetricTypes {
    REGISTRATION, SYNC, ACTIONS, PERSISTENCE_HANDLER, PERSISTENCE_TRANSACTION;
}

fun getTimerMetric(
    type: TimerMetricTypes,
    holdingId: net.corda.data.identity.HoldingIdentity?,
    operation: String
): Timer {
    return when (type) {
        REGISTRATION -> Membership.RegistrationHandlerExecutionTime
        SYNC -> Membership.SyncHandlerExecutionTime
        ACTIONS -> Membership.ActionsHandlerExecutionTime
        PERSISTENCE_HANDLER -> Membership.PersistenceHandlerExecutionTime
        PERSISTENCE_TRANSACTION -> Membership.PersistenceTransactionExecutionTime
    }.builder()
        .withTag(CordaMetrics.Tag.OperationName, operation)
        .withTag(CordaMetrics.Tag.MembershipGroup, holdingId?.groupId ?: "unspecified")
        .forVirtualNode(holdingId?.toCorda()?.shortHash?.value ?: "unspecified")
        .build()
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
