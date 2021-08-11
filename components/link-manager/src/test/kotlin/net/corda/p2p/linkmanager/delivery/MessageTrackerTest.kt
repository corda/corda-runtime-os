package net.corda.p2p.linkmanager.delivery

import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.payload.FlowMessage
import org.junit.jupiter.api.Test
import org.osgi.test.common.annotation.InjectService
import java.nio.ByteBuffer

class MessageTrackerTest {

    companion object {
        const val REPLAY_PERIOD = 100L
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    private val messageReplayer = DeliveryTracker.FlowMessageReplayer(publisherFactory, subscriptionFactory, MockCallback::replayMessage)

    object MockCallback {
        val events = mutableListOf<EventLogRecord<ByteBuffer, FlowMessage>>()

        fun replayMessage(event: EventLogRecord<ByteBuffer, FlowMessage>): List<Record<String, *>> {
            events.add(event)
            return emptyList()
        }
    }

    val replayManager = ReplayScheduler(REPLAY_PERIOD, messageReplayer::replayMessage)



    @Test
    fun `Messages are tracked`() {

    }

}