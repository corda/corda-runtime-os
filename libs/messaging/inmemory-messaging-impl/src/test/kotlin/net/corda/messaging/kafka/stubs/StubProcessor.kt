package net.corda.messaging.kafka.stubs

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

/**
 * Stubbed processor to count interactions and trigger errors.
 */
class StubProcessor(private val latch: CountDownLatch, private val exception: Exception? = null) : PubSubProcessor<String, ByteBuffer> {
    override fun onNext(event: Record<String, ByteBuffer>) {
        latch.countDown()

        if (exception != null) {
            throw exception
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ByteBuffer>
        get() = ByteBuffer::class.java
}