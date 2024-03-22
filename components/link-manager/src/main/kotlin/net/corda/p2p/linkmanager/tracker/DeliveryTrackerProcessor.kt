package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor

internal class DeliveryTrackerProcessor(
    private val outboundMessageProcessor: OutboundMessageProcessor,
    private val partitionsStates: PartitionsStates,
    private val publisher: PublisherWithDominoLogic,
) : EventLogProcessor<String, AppMessage> {
    override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<*, *>> {
        partitionsStates.read(events)
        val records = outboundMessageProcessor.onNext(events)
        publisher.publish(records).forEach {
            it.join()
        }
        partitionsStates.sent(events)
        return emptyList()
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
}
