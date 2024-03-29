package net.corda.p2p.linkmanager.metrics

import io.micrometer.core.instrument.Counter
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage
import net.corda.data.p2p.event.SessionDirection
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.NOT_APPLICABLE_TAG_VALUE
import net.corda.virtualnode.HoldingIdentity
import java.time.Duration
import java.time.Instant

const val P2P_SUBSYSTEM = "p2p"
const val SESSION_MESSAGE_TYPE = "SessionMessage"

fun recordOutboundMessagesMetric(message: AuthenticatedMessage) {
    message.header.let {
        recordOutboundMessagesMetric(it.source.groupId,
            it.subsystem, message::class.java.simpleName)
    }
}

fun recordOutboundMessagesMetric(message: OutboundUnauthenticatedMessage) {
    message.header.let {
        recordOutboundMessagesMetric(it.source.groupId,
            it.subsystem, message::class.java.simpleName)
    }
}

fun recordOutboundSessionMessagesMetric(sourceVnode: HoldingIdentity) {
    recordOutboundMessagesMetric(sourceVnode.groupId,
        P2P_SUBSYSTEM, SESSION_MESSAGE_TYPE)
}

fun recordOutboundSessionMessagesMetric(sourceVnode: net.corda.data.identity.HoldingIdentity) {
    recordOutboundMessagesMetric(sourceVnode.groupId,
        P2P_SUBSYSTEM, SESSION_MESSAGE_TYPE)
}

fun recordOutboundMessagesMetric(group: String, subsystem: String, messageType: String) {
    CordaMetrics.Metric.OutboundMessageCount.builder()
        .withTag(CordaMetrics.Tag.MembershipGroup, group)
        .withTag(CordaMetrics.Tag.MessagingSubsystem, subsystem)
        .withTag(CordaMetrics.Tag.MessageType, messageType)
        .build().increment()
}

fun recordInboundMessagesMetric(message: AuthenticatedMessage) {
    message.header.let {
        recordInboundMessagesMetric(it.source.groupId, it.subsystem, message::class.java.simpleName)
    }
}

fun recordInboundMessagesMetric(message: InboundUnauthenticatedMessage) {
    recordInboundMessagesMetric(null, message.header.subsystem, message::class.java.simpleName)
}

fun recordInboundSessionMessagesMetric(datapoints: Int = 1) {
    repeat(datapoints) {
        recordInboundMessagesMetric(null, P2P_SUBSYSTEM, SESSION_MESSAGE_TYPE)
    }
}

private fun recordInboundMessagesMetric(group: String?, subsystem: String, messageType: String) {
    val builder = CordaMetrics.Metric.InboundMessageCount.builder()
    listOf(
        CordaMetrics.Tag.MembershipGroup to group,
        CordaMetrics.Tag.MessagingSubsystem to subsystem,
        CordaMetrics.Tag.MessageType to messageType,
    ).forEach {
        val value = it.second ?: NOT_APPLICABLE_TAG_VALUE
        builder.withTag(it.first, value)
    }
    builder.build().increment()
}

fun recordSessionTimeoutMetric(source: HoldingIdentity, direction: SessionDirection) {
    CordaMetrics.Metric.SessionTimeoutCount.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .withTag(CordaMetrics.Tag.MembershipGroup, source.groupId)
        .build().increment()
}

fun recordSessionCreationTime(startTime: Instant) {
    CordaMetrics.Metric.SessionCreationTime.builder()
        .build()
        .record(Duration.between(startTime, Instant.now()))
}

fun recordP2PMetric(metric: CordaMetrics.Metric<Counter>, direction: SessionDirection, incrementBy: Double = 1.0) {
    metric.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .build().increment(incrementBy)
}