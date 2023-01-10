package net.corda.flow.dummy.link

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.schema.Schemas

/**
 * Processes events from the P2P.out topic.
 * Moves them to P2P.in
 */
class DummyLinkManagerProcessor: DurableProcessor<String, AppMessage> {

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> {
        return events.map {
            Record(Schemas.P2P.P2P_IN_TOPIC, it.key, it.value)
        }
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
}
