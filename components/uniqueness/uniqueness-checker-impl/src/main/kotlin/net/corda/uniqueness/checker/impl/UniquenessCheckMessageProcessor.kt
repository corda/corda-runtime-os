package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.UniquenessCheckRequest
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.uniqueness.checker.UniquenessChecker

class UniquenessCheckMessageProcessor(
    private val uniquenessChecker: UniquenessChecker,
    private val externalEventResponseFactory: ExternalEventResponseFactory
): DurableProcessor<String, UniquenessCheckRequest> {

    override val keyClass = String::class.java
    override val valueClass = UniquenessCheckRequest::class.java

    override fun onNext(events: List<Record<String, UniquenessCheckRequest>>): List<Record<*, *>> {

        val requests = events.map { it.value }.filterNotNull()

        return requests.zip(uniquenessChecker.processRequests(requests)).map { (request, response) ->
            // TODO - need to rethink exception handling and whether we should be throwing exceptions
            // with platform errors or not
            externalEventResponseFactory.success(request.flowExternalEventContext, response)
        }
    }
}
