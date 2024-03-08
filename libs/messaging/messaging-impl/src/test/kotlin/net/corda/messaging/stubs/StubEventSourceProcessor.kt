package net.corda.messaging.stubs

import net.corda.messaging.api.processor.EventSourceProcessor
import net.corda.messaging.api.records.EventLogRecord
import java.util.concurrent.CountDownLatch

class StubEventSourceProcessor<K: Any, V: Any>(private val invocationLatch: CountDownLatch,
                                               private val eventsLatch: CountDownLatch,
                                               private val exception: Exception? = null,
                                               override val keyClass: Class<K>,
                                               override val valueClass: Class<V>): EventSourceProcessor<K, V> {

    override fun onNext(events: List<EventLogRecord<K, V>>){
        invocationLatch.countDown()
        events.forEach { _ -> eventsLatch.countDown() }

        if (exception != null) {
            throw exception
        }
    }
}