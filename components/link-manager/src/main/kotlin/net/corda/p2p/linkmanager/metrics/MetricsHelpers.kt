@file:Suppress("TooManyFunctions")
package net.corda.p2p.linkmanager.metrics

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

fun recordSessionTimeoutMetric(source: HoldingIdentity, direction: SessionDirection) {
    CordaMetrics.Metric.SessionTimeoutCount.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .withTag(CordaMetrics.Tag.MembershipGroup, source.groupId)
        .build().increment()
}

fun recordSessionDeletedMetric(direction: SessionDirection) {
    CordaMetrics.Metric.SessionDeletedCount.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .build().increment()
}

fun recordSessionStartedMetric(direction: SessionDirection) {
    CordaMetrics.Metric.SessionStartedCount.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .build().increment()
}

fun recordSessionEstablishedMetric(direction: SessionDirection) {
    CordaMetrics.Metric.SessionEstablishedCount.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .build().increment()
}

fun recordSessionFailedMetric(direction: SessionDirection) {
    CordaMetrics.Metric.SessionFailedCount.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .build().increment()
}

fun recordSessionCreationTime(startTime: Long) {
    CordaMetrics.Metric.SessionCreationTime.builder()
        .build()
        .record(Duration.ofNanos(Instant.now().toEpochMilli() - startTime))
}

fun recordSessionMessageReplayMetric(direction: SessionDirection) {
    CordaMetrics.Metric.SessionMessageReplayCount.builder()
        .withTag(CordaMetrics.Tag.SessionDirection, direction.toString())
        .build().increment()
}