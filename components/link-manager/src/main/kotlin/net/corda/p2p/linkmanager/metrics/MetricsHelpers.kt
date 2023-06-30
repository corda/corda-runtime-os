package net.corda.p2p.linkmanager.metrics

import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.NOT_APPLICABLE_TAG_VALUE

fun recordInboundMessagesMetric(message: AuthenticatedMessage) {
    message.header.let {
        recordInboundMessagesMetric(it.source.x500Name, it.destination.x500Name, it.source.groupId,
            it.subsystem, message::class.java.simpleName)
    }
}

fun recordInboundMessagesMetric(message: InboundUnauthenticatedMessage) {
    recordInboundMessagesMetric(null, null, null,
        message.header.subsystem, message::class.java.simpleName)
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