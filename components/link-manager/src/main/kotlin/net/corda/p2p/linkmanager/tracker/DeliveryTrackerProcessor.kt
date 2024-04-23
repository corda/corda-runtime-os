package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.EventSourceProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor

internal class DeliveryTrackerProcessor(
    private val outboundMessageProcessor: OutboundMessageProcessor,
    private val handler: MessagesHandler,
    private val publisher: PublisherWithDominoLogic,
) : EventSourceProcessor<String, AppMessage> {
    override fun onNext(events: List<EventLogRecord<String, AppMessage>>) {
        val eventsToForward = handler.handleMessagesAndFilterRecords(events)
        val records = outboundMessageProcessor.onNext(eventsToForward)
        publisher.publish(records).forEach {
            it.join()
        }
        handler.handled(events)
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
}
