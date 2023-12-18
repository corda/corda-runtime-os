package net.corda.session.mapper.service.integration

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future

class TestFlowMapperProcessor(
    private val latch: CountDownLatch,
    private val records: MutableList<SessionEvent>
): PubSubProcessor<String, FlowMapperEvent> {

    override fun onNext(event: Record<String, FlowMapperEvent>): Future<Unit> {
        latch.countDown()
        val sessionEvent = event.value?.payload as? SessionEvent ?: throw IllegalArgumentException("Not a session event")
        records.add(sessionEvent)
        return CompletableFuture.completedFuture(Unit)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<FlowMapperEvent>
        get() = FlowMapperEvent::class.java
}