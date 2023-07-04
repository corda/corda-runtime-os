package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.tracing.BatchRecordTracer
import net.corda.tracing.traceBatch
import net.corda.uniqueness.checker.UniquenessChecker

/**
 * Processes messages received from the uniqueness check topic, and responds using the external
 * events response API.
 */
class UniquenessCheckMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : DurableProcessor<String, UniquenessCheckRequestAvro> {

    override val keyClass = String::class.java
    override val valueClass = UniquenessCheckRequestAvro::class.java

    override fun onNext(events: List<Record<String, UniquenessCheckRequestAvro>>): List<Record<*, *>> {

        val batchTracer = createBatchTracer(events)

        val requests = events.mapNotNull { it.value }

        return uniquenessChecker.processRequests(requests).map { (request, response) ->
            if (response.result is UniquenessCheckResultUnhandledExceptionAvro) {
                batchTracer.error(
                    request,
                    externalEventResponseFactory.platformError(
                        request.flowExternalEventContext,
                        (response.result as UniquenessCheckResultUnhandledExceptionAvro).exception
                    )
                )
            } else {
                batchTracer.complete(
                    request,
                    externalEventResponseFactory.success(request.flowExternalEventContext, response)
                )
            }
        }
    }
}

private fun BatchRecordTracer.error(request: UniquenessCheckRequestAvro, record: Record<*, *>): Record<*, *> {
    return request.flowExternalEventContext?.requestId?.let { id -> this.completeSpanFor(id, record) } ?: record
}

private fun BatchRecordTracer.complete(request: UniquenessCheckRequestAvro, record: Record<*, *>): Record<*, *> {
    return request.flowExternalEventContext?.requestId?.let { id -> this.completeSpanFor(id, record) } ?: record
}

private fun createBatchTracer(events: List<Record<String, UniquenessCheckRequestAvro>>): BatchRecordTracer {
    return traceBatch("Uniqueness Check Request").apply {
        events.forEach { event ->
            val id = event.value?.flowExternalEventContext?.requestId
            if (id != null) {
                this.startSpanFor(event, id)
            }
        }
    }
}
