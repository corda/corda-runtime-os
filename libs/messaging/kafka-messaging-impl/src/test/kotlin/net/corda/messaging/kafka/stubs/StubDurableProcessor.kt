package net.corda.messaging.kafka.stubs

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

class StubDurableProcessor(
    private val invocationLatch: CountDownLatch,
    private val eventsLatch: CountDownLatch,
    private val exception: Exception? = null
) :
    DurableProcessor<String, ByteBuffer> {
    override fun onNext(events: List<Record<String, ByteBuffer>>): List<Record<*, *>> {
        invocationLatch.countDown()
        events.forEach { _ -> eventsLatch.countDown() }

        if (exception != null) {
            throw exception
        }

        return events
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ByteBuffer>
        get() = ByteBuffer::class.java
}
