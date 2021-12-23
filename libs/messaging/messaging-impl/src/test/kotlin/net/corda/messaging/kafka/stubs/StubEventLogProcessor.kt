package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class StubEventLogProcessor<K: Any, V: Any>(private val invocationLatch: CountDownLatch,
                                            private val eventsLatch: CountDownLatch,
                                            private val exception: Exception? = null,
                                            override val keyClass: Class<K>,
                                            override val valueClass: Class<V>): EventLogProcessor<K, V> {

    override fun onNext(events: List<EventLogRecord<K, V>>): List<Record<*, *>> {
        invocationLatch.countDown()
        events.forEach { _ -> eventsLatch.countDown() }

        if (exception != null) {
            throw exception
        }

        return emptyList()
    }

}