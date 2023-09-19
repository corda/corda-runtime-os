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
class UniquenessCheckRpcMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    override val requestClass: Class<UniquenessCheckRequestAvro>,
    override val responseClass: Class<FlowEvent>,
) : SyncRPCProcessor<UniquenessCheckRequestAvro, FlowEvent> {

    override fun process(request: UniquenessCheckRequestAvro): FlowEvent {
        return uniquenessChecker.processRequests(listOf(request)).map { (request, response) ->
            if (response.result is UniquenessCheckResultUnhandledExceptionAvro) {
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
