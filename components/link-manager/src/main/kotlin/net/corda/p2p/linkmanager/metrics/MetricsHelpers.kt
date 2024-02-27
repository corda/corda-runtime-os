package net.corda.p2p.linkmanager.metrics

import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.NOT_APPLICABLE_TAG_VALUE
import net.corda.virtualnode.HoldingIdentity

const val P2P_SUBSYSTEM = "p2p"
const val SESSION_MESSAGE_TYPE = "SessionMessage"
const val HEARTBEAT_MESSAGE = "HeartbeatMessage"

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

fun recordOutboundHeartbeatMessagesMetric(sourceVnode: HoldingIdentity) {
    recordOutboundMessagesMetric(sourceVnode.groupId,
        P2P_SUBSYSTEM, HEARTBEAT_MESSAGE)
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

fun recordInboundHeartbeatMessagesMetric(destinationVnode: HoldingIdentity) {
    recordInboundMessagesMetric(destinationVnode.groupId, P2P_SUBSYSTEM, HEARTBEAT_MESSAGE)
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

fun recordOutboundSessionTimeoutMetric(source: HoldingIdentity) {
    CordaMetrics.Metric.OutboundSessionTimeoutCount.builder()
        .withTag(CordaMetrics.Tag.MembershipGroup, source.groupId)
        .build().increment()
}

fun recordInboundSessionTimeoutMetric(source: HoldingIdentity) {
    CordaMetrics.Metric.InboundSessionTimeoutCount.builder()
        .withTag(CordaMetrics.Tag.MembershipGroup, source.groupId)
        .build().increment()
}