package net.corda.uniqueness.checker.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.HttpRPCProcessor
import net.corda.uniqueness.checker.UniquenessChecker

/**
 * Processes messages received from the RPC calls, and responds using the external
 * events response API.
 */
class UniquenessCheckRpcMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    override val reqClass: Class<UniquenessCheckRequestAvro>,
    override val respClass: Class<FlowEvent>
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
}
