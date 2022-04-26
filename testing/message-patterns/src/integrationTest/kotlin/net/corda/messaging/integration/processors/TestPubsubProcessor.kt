package net.corda.messaging.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class TestPubsubProcessor(
    private val latch: AtomicReference<CountDownLatch>,
    private val completeFuture: Boolean = true
) : PubSubProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java
    val future = AtomicReference<CompletableFuture<Unit>>(null)

    override fun onNext(event: Record<String, DemoRecord>): CompletableFuture<Unit> {
        latch.get().countDown()
        val thisFuture = CompletableFuture<Unit>()
        if (completeFuture) {
            thisFuture.complete(Unit)
        }
        future.set(thisFuture)
        return thisFuture
    }
}
