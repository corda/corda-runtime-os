package net.corda.messaging.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.data.demo.DemoStateRecord
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestDurableProcessor(
    private val latch: CountDownLatch, private val outputTopic: String? = null, private val delayProcessor: Long? = null
) : DurableProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(events: List<Record<String, DemoRecord>>): List<Record<*, *>> {
        if (delayProcessor != null) {
            Thread.sleep(delayProcessor)
        }

        for (event in events) {
            latch.countDown()
        }

        return if (outputTopic != null) {
            listOf(Record(outputTopic, "durableOutputKey", DemoRecord(1)))
        } else {
            emptyList<Record<String, DemoRecord>>()
        }
    }
}

class TestDurableStringProcessor(
    private val latch: CountDownLatch, private val outputTopic: String? = null, private val delayProcessor: Long? = null
) : DurableProcessor<String, String> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        if (delayProcessor != null) {
            Thread.sleep(delayProcessor)
        }

        for (event in events) {
            latch.countDown()
        }

        return if (outputTopic != null) {
            listOf(Record(outputTopic, "durableOutputKey", "test"))
        } else {
            emptyList<Record<String, String>>()
        }
    }
}

class TestDurableDummyMessageProcessor(
    private val latch: CountDownLatch, private val outputTopic: String? = null, private val delayProcessor: Long? = null
) : DurableProcessor<String, DemoStateRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoStateRecord>
        get() = DemoStateRecord::class.java

    override fun onNext(events: List<Record<String, DemoStateRecord>>): List<Record<*, *>> {
        if (delayProcessor != null) {
            Thread.sleep(delayProcessor)
        }

        for (event in events) {
            latch.countDown()
        }

        return if (outputTopic != null) {
            listOf(Record(outputTopic, "durableOutputKey", DemoStateRecord(1)))
        } else {
            emptyList<Record<String, DemoStateRecord>>()
        }
    }
}
