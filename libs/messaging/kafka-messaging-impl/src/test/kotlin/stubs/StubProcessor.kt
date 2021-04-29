package net.corda.messaging.kafka.subscription.stubs

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import java.lang.Exception
import java.util.concurrent.CountDownLatch

class StubProcessor(private val latch: CountDownLatch, private val throwFatalError: Boolean = false) : PubSubProcessor<String, ByteArray> {
    override fun onNext(event: Record<String, ByteArray>) {
        latch.countDown()

        if (throwFatalError) {
            throw CordaMessageAPIFatalException("", Exception())
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ByteArray>
        get() = ByteArray::class.java
}