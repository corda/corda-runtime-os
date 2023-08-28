package net.corda.uniqueness.checker.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.HttpRPCProcessor
import net.corda.uniqueness.checker.UniquenessChecker

/**
 * Processes messages received from the uniqueness check topic, and responds using the external
 * events response API.
 */
class UniquenessCheckMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : HttpRPCProcessor<UniquenessCheckRequestAvro, FlowEvent> {

    override fun process(request: UniquenessCheckRequestAvro): FlowEvent {
        val result = uniquenessChecker.processRequests(listOf(request))

        return result.map { (request, response) ->
            if (response.result is UniquenessCheckResultUnhandledExceptionAvro) {

                    externalEventResponseFactory.platformError(
                        request.flowExternalEventContext,
                        (response.result as UniquenessCheckResultUnhandledExceptionAvro).exception
                    )


            } else {
                    externalEventResponseFactory.success(request.flowExternalEventContext, response)
            }
        }.first().value!!
    }

    override val reqClazz: Class<UniquenessCheckRequestAvro>
        get() = UniquenessCheckRequestAvro::class.java
    override val respClazz: Class<FlowEvent>
        get() = FlowEvent::class.java
}


/*
private fun BatchRecordTracer.error(request: UniquenessCheckRequestAvro, record: Record<*, *>): Record<*, *> {
    return request.flowExternalEventContext?.requestId?.let { id -> this.completeSpanFor(id, record) } ?: record
}

private fun BatchRecordTracer.complete(request: UniquenessCheckRequestAvro, record: Record<*, *>): Record<*, *> {
    return request.flowExternalEventContext?.requestId?.let { id -> this.completeSpanFor(id, record) } ?: record
}

private fun createBatchTracer(event: UniquenessCheckRequestAvro, headers: Map<String, String>): BatchRecordTracer {
    return traceBatch("Uniqueness Check Request").apply {
            val id = event.flowExternalEventContext?.requestId
            if (id != null) {
                this.startSpanFor(event, id)
            }

    }
}
*/
