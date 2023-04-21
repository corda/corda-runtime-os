package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.uniqueness.checker.UniquenessChecker

/**
 * Processes messages received from the uniqueness check topic, and responds using the external
 * events response API.
 */
class UniquenessCheckMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory
): DurableProcessor<String, UniquenessCheckRequestAvro> {

    override val keyClass = String::class.java
    override val valueClass = UniquenessCheckRequestAvro::class.java

    override fun onNext(events: List<Record<String, UniquenessCheckRequestAvro>>): List<Record<*, *>> {

        val requests = events.mapNotNull { it.value }

        return uniquenessChecker.processRequests(requests).map { (request, response) ->
            if (response.result is UniquenessCheckResultUnhandledExceptionAvro) {
                externalEventResponseFactory.platformError(
                    request.flowExternalEventContext,
                    (response.result as UniquenessCheckResultUnhandledExceptionAvro).exception)
            } else {
                externalEventResponseFactory.success(request.flowExternalEventContext, response)
            }
        }
    }
}
