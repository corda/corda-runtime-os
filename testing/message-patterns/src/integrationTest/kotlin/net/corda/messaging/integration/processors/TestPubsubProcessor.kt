package net.corda.messaging.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class TestPubsubProcessor(private val latch: CountDownLatch) : PubSubProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(event: Record<String, DemoRecord>): CompletableFuture<Unit> {
        latch.countDown()
        return CompletableFuture.completedFuture(Unit)
    }
}
