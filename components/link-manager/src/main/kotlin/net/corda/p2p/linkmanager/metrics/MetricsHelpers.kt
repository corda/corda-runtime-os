package net.corda.p2p.linkmanager.metrics

import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.NOT_APPLICABLE_TAG_VALUE
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory

const val P2P_SUBSYSTEM = "p2p"
const val SESSION_MESSAGE_TYPE = "SessionMessage"
const val HEARTBEAT_MESSAGE = "HeartbeatMessage"

private val logger = LoggerFactory.getLogger("MetricsHelper")

fun recordOutboundMessagesMetric(message: AuthenticatedMessage) {
    message.header.let {
        logger.info("Emitting outbound authenticated data messages metric")
        recordOutboundMessagesMetric(it.source.x500Name, it.destination.x500Name, it.source.groupId,
            it.subsystem, message::class.java.simpleName)
    }
}

fun recordOutboundMessagesMetric(message: OutboundUnauthenticatedMessage) {
    message.header.let {
        logger.info("Emitting outbound unauthenticated data messages metric")
        recordOutboundMessagesMetric(it.source.x500Name, it.destination.x500Name, it.source.groupId,
            it.subsystem, message::class.java.simpleName)
    }
}

fun recordOutboundSessionMessagesMetric(sourceVnode: HoldingIdentity, destinationVnode: HoldingIdentity) {
    logger.info("Emitting outbound session messages metric")
    recordOutboundMessagesMetric(sourceVnode.x500Name.toString(), destinationVnode.x500Name.toString(), sourceVnode.groupId,
        P2P_SUBSYSTEM, SESSION_MESSAGE_TYPE)
}

fun recordOutboundHeartbeatMessagesMetric(sourceVnode: HoldingIdentity, destinationVnode: HoldingIdentity) {
    logger.info("Emitting outbound heartbeat messages metric")
    recordOutboundMessagesMetric(sourceVnode.x500Name.toString(), destinationVnode.x500Name.toString(), sourceVnode.groupId,
        P2P_SUBSYSTEM, HEARTBEAT_MESSAGE)
}

fun recordOutboundSessionMessagesMetric(sourceVnode: net.corda.data.identity.HoldingIdentity,
                                        destinationVnode: net.corda.data.identity.HoldingIdentity) {
    logger.info("Emitting outbound session messages metric")
    recordOutboundMessagesMetric(sourceVnode.x500Name, destinationVnode.x500Name, sourceVnode.groupId,
        P2P_SUBSYSTEM, SESSION_MESSAGE_TYPE)
}

fun recordOutboundMessagesMetric(source: String, dest: String, group: String, subsystem: String, messageType: String) {
    CordaMetrics.Metric.OutboundMessageCount.builder()
        .withTag(CordaMetrics.Tag.SourceVirtualNode, source)
        .withTag(CordaMetrics.Tag.DestinationVirtualNode, dest)
        .withTag(CordaMetrics.Tag.MembershipGroup, group)
        .withTag(CordaMetrics.Tag.MessagingSubsystem, subsystem)
        .withTag(CordaMetrics.Tag.MessageType, messageType)
        .build().increment()
}

fun recordInboundMessagesMetric(message: AuthenticatedMessage) {
    logger.info("Emitting inbound authenticated data messages metric")
    message.header.let {
        recordInboundMessagesMetric(it.source.x500Name, it.destination.x500Name, it.source.groupId,
            it.subsystem, message::class.java.simpleName)
    }
}

fun recordInboundMessagesMetric(message: InboundUnauthenticatedMessage) {
    logger.info("Emitting inbound unauthenticated data messages metric")
    recordInboundMessagesMetric(null, null, null,
        message.header.subsystem, message::class.java.simpleName)
}

fun recordInboundSessionMessagesMetric(datapoints: Int = 1) {
    logger.info("Emitting inbound session messages metric ($datapoints data points)")
    repeat(datapoints) {
        recordInboundMessagesMetric(null, null, null, P2P_SUBSYSTEM, SESSION_MESSAGE_TYPE)
    }
}

fun recordInboundHeartbeatMessagesMetric(sourceVnode: HoldingIdentity,
                                         destinationVnode: HoldingIdentity) {
    logger.info("Emitting inbound heartbeat messages metric")
    recordInboundMessagesMetric(sourceVnode.x500Name.toString(), destinationVnode.x500Name.toString(),
        destinationVnode.groupId, P2P_SUBSYSTEM, HEARTBEAT_MESSAGE)
}

private fun recordInboundMessagesMetric(source: String?, dest: String?, group: String?, subsystem: String, messageType: String) {
    val builder = CordaMetrics.Metric.InboundMessageCount.builder()
    listOf(
        CordaMetrics.Tag.SourceVirtualNode to source,
        CordaMetrics.Tag.DestinationVirtualNode to dest,
        CordaMetrics.Tag.MembershipGroup to group,
        CordaMetrics.Tag.MessagingSubsystem to subsystem,
        CordaMetrics.Tag.MessageType to messageType,
    ).forEach {
        val value = it.second ?: NOT_APPLICABLE_TAG_VALUE
        builder.withTag(it.first, value)
    }
    builder.build().increment()
}

fun recordOutboundSessionTimeoutMetric(source: HoldingIdentity, destination: HoldingIdentity) {
    CordaMetrics.Metric.OutboundSessionTimeoutCount.builder()
        .withTag(CordaMetrics.Tag.SourceVirtualNode, source.x500Name.toString())
        .withTag(CordaMetrics.Tag.DestinationVirtualNode, destination.x500Name.toString())
        .withTag(CordaMetrics.Tag.MembershipGroup, source.groupId)
        .build().increment()
}

fun recordInboundSessionTimeoutMetric(source: HoldingIdentity, destination: HoldingIdentity?) {
    CordaMetrics.Metric.InboundSessionTimeoutCount.builder()
        .withTag(CordaMetrics.Tag.SourceVirtualNode, source.x500Name.toString())
        .withTag(CordaMetrics.Tag.DestinationVirtualNode, destination?.x500Name?.toString() ?: NOT_APPLICABLE_TAG_VALUE)
        .withTag(CordaMetrics.Tag.MembershipGroup, source.groupId)
        .build().increment()
}