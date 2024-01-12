package net.corda.p2p.linkmanager.inbound
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.messaging.api.records.Record

internal data class InboundResponse(
    val records: List<Record<*, *>>,
    val httpReply: LinkManagerResponse? = null,
) {
    fun plus(record: Record<*, *>): InboundResponse {
        return InboundResponse(
            records + record,
            httpReply,
        )
    }
}