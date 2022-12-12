package net.corda.processors.crypto.tests.infra

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import net.corda.data.CordaAvroDeserializer
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.concurrent.getOrThrow

class FlowOpsResponses(
    private val deserializer: CordaAvroDeserializer<FlowOpsResponse>
) : DurableProcessor<String, FlowEvent> {

    private val receivedEvents = ConcurrentHashMap<String, CompletableFuture<FlowOpsResponse?>>()

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<FlowEvent> = FlowEvent::class.java

    override fun onNext(events: List<Record<String, FlowEvent>>): List<Record<*, *>> {
        events.forEach {
            val response = ((it.value as FlowEvent).payload as ExternalEventResponse)
            val flowOpsResponse = deserializer.deserialize(response.payload.array())
            val future = receivedEvents.computeIfAbsent(it.key) {
                // If future is already set for this key means testing thread has already called `waitForResponse`
                // for this key.
                CompletableFuture()
            }
            future.complete(flowOpsResponse)
        }
        return emptyList()
    }

    fun waitForResponse(key: String): FlowOpsResponse {
        val future: Future<FlowOpsResponse?> = receivedEvents.computeIfAbsent(key) {
            CompletableFuture()
        }

        val flowOpsResponse = future.getOrThrow(Duration.ofSeconds(20))
        return requireNotNull(flowOpsResponse)
    }
}