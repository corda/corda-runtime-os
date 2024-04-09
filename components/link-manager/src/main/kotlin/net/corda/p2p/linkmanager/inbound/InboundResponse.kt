package net.corda.p2p.linkmanager.inbound
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.messaging.api.records.Record
import net.corda.utilities.Either

internal data class InboundResponse(
    val records: List<Record<*, *>>,
    val ack: Either<LinkManagerResponse, Record<String, LinkOutMessage>>? = null,
)