package net.corda.session.mapper.service.integration

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestProcessor(
    private val latch: CountDownLatch,
) : DurableProcessor<Any, Any> {

    override val keyClass = Any::class.java
    override val valueClass = Any::class.java
    override fun onNext(events: List<Record<Any, Any>>): List<Record<*, *>> {
        latch.countDown()
        return emptyList()
    }
}
