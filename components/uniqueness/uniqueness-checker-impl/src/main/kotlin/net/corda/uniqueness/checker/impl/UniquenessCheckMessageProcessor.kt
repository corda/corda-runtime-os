package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
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

        val requests = events.map { it.value }.filterNotNull()

        return requests.zip(uniquenessChecker.processRequests(requests)).map { (request, response) ->
            // TODO - need to rethink exception handling and whether we should be throwing exceptions
            // with platform errors or not
            externalEventResponseFactory.success(request.flowExternalEventContext, response)
        }
    }
}
