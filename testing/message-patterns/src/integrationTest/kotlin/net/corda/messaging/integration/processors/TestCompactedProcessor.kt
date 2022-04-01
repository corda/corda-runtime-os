package net.corda.messaging.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestCompactedProcessor(private val snapshotLatch: CountDownLatch, private val onNextLatch: CountDownLatch) :
    CompactedProcessor<String,
            DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onSnapshot(currentData: Map<String, DemoRecord>) {
        snapshotLatch.countDown()
    }

    override fun onNext(newRecord: Record<String, DemoRecord>, oldValue: DemoRecord?, currentData: Map<String, DemoRecord>) {
        onNextLatch.countDown()
    }
}
