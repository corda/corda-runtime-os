package net.corda.uniqueness.checker.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.libs.uniqueness.UniquenessChecker
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.uniqueness.checker.impl.UniquenessCheckerAvroUtils.toAvro
import net.corda.uniqueness.checker.impl.UniquenessCheckerAvroUtils.toCorda
import net.corda.uniqueness.datamodel.common.toAvro
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorUnhandledException
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure

/**
 * Processes messages received from the RPC calls, and responds using the external
 * events response API.
 */
@Suppress("Unused")
class UniquenessCheckMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
) : SyncRPCProcessor<UniquenessCheckRequestAvro, FlowEvent> {

    override val requestClass = UniquenessCheckRequestAvro::class.java
    override val responseClass = FlowEvent::class.java

    override fun process(request: UniquenessCheckRequestAvro): FlowEvent {
        return uniquenessChecker.processRequests(listOf(request.toCorda())).map { (_, response) ->
            val result = response.uniquenessCheckResult
            // TODO Remove this error type check when we have a proper api code path for notarization checking
            if (result is UniquenessCheckResultFailure && result.error is UniquenessCheckErrorUnhandledException) {
                externalEventResponseFactory.platformError(
                    request.flowExternalEventContext,
                    (result.toAvro() as UniquenessCheckResultUnhandledExceptionAvro).exception
                )
            } else {
                externalEventResponseFactory.success(request.flowExternalEventContext, response.toAvro())
            }
        }.single().value!!
    }
}
