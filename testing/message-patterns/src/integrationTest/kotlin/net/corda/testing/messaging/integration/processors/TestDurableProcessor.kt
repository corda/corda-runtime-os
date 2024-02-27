package net.corda.testing.messaging.integration.processors

import net.corda.data.demo.DemoRecord
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
            listOf(Record(outputTopic, "durableOutputKey", DemoRecord()))
        } else {
            emptyList<Record<String, DemoRecord>>()
        }
    }
}

class TestBadSerializationDurableProcessor(
    private val latch: CountDownLatch, private val outputTopic: String = "output4", private val badRecord: Int? = null
) : DurableProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(events: List<Record<String, DemoRecord>>): List<Record<*, *>> {
        val outputlist = mutableListOf<Record<*, *>>()

        for (event in events) {
            if (badRecord != null && badRecord == event.value?.value) {
                outputlist.add(Record(outputTopic, event.key, TestDurableDummyMessageProcessor(latch)))
            }
            latch.countDown()
        }

        return outputlist
    }
}

class TestDLQDurableProcessor(
    private val latch: CountDownLatch
) : DurableProcessor<String, ByteArray> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ByteArray>
        get() = ByteArray::class.java

    override fun onNext(events: List<Record<String, ByteArray>>): List<Record<*, *>> {

        for (event in events) {
            latch.countDown()
        }

        return emptyList()
    }
}
