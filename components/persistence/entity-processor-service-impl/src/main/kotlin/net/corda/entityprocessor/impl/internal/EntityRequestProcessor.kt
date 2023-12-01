package net.corda.entityprocessor.impl.internal

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepository
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepositoryImpl
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * The [EntityRequest] contains the request and a typed payload.
 *
 * The [EntityResponse] contains the response or an exception-like payload whose presence indicates
 * an error has occurred.
 *
 */

@Suppress("LongParameterList")
class EntityRequestProcessor(
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    private val entitySandboxService: EntitySandboxService,
    private val responseFactory: ResponseFactory,
    private val requestsIdsRepository: RequestsIdsRepository = RequestsIdsRepositoryImpl()
) : SyncRPCProcessor<EntityRequest, FlowEvent> {

    override val requestClass = EntityRequest::class.java
    override val responseClass = FlowEvent::class.java

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val processorService = ProcessorService()
    }

    override fun process(request: EntityRequest): FlowEvent {
        logger.debug { "process processing request $request" }

        val record = processorService.processEvent(
            logger, request, entitySandboxService, currentSandboxGroupContext, responseFactory, requestsIdsRepository
        )
        return record.value as FlowEvent
    }
}