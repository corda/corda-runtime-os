package net.corda.uniqueness.checker.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.uniqueness.checker.UniquenessChecker

/**
 * Processes messages received from the RPC calls, and responds using the external
 * events response API.
 */
class UniquenessCheckMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
) : SyncRPCProcessor<UniquenessCheckRequestAvro, FlowEvent> {

    override val requestClass = UniquenessCheckRequestAvro::class.java
    override val responseClass = FlowEvent::class.java

    override fun process(request: UniquenessCheckRequestAvro): FlowEvent {
        return uniquenessChecker.processRequests(listOf(request)).map { (request, response) ->
            val result = response.result
            // TODO Remove this error type check when we have a proper api code path for notarization checking
            if (result is UniquenessCheckResultUnhandledExceptionAvro && result.exception.errorType != "UniquenessCheckErrorNotPreviouslyNotarizedException") {
                externalEventResponseFactory.platformError(
                    request.flowExternalEventContext,
                    (response.result as UniquenessCheckResultUnhandledExceptionAvro).exception
                )
            } else {
                externalEventResponseFactory.success(request.flowExternalEventContext, response)
            }
        }.single().value!!
    }
}

