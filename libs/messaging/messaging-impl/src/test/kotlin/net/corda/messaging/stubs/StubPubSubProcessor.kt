package net.corda.messaging.stubs

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class StubPubSubProcessor(
    private val latch: CountDownLatch,
    private val exception: Exception? = null,
    private val futureException: Exception? = null,
) : PubSubProcessor<String, ByteBuffer> {
    override fun onNext(event: Record<String, ByteBuffer>): CompletableFuture<Unit> {
        latch.countDown()

        if (exception != null) {
            throw exception
        }
        return if (futureException != null) {
            CompletableFuture.failedFuture(futureException)
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ByteBuffer>
        get() = ByteBuffer::class.java
}
