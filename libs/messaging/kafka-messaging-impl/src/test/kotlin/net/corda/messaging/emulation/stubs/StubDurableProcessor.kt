package net.corda.messaging.kafka.subscription.net.corda.messaging.emulation.stubs

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

class StubDurableProcessor(private val latch: CountDownLatch, private val exception: Exception? = null) :
    DurableProcessor<String, ByteBuffer> {
    override fun onNext(event: Record<String, ByteBuffer>): List<Record<*, *>> {
        latch.countDown()

        if (exception != null) {
            throw exception
        }

        return listOf(event)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ByteBuffer>
        get() = ByteBuffer::class.java
}