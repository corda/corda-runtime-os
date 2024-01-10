package net.corda.messaging.integration.processors

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestDurableProcessorStrings(
    private val latch: CountDownLatch
) : DurableProcessor<String, String> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        for (event in events) {
            latch.countDown()
        }

        return emptyList<Record<String, String>>()
    }
}
