package net.corda.entityprocessor.impl.internal

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.DeleteEntities
import net.corda.data.persistence.DeleteEntitiesById
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntities
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.data.persistence.MergeEntities
import net.corda.data.persistence.PersistEntities
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.utilities.withMDC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

fun EntitySandboxService.getClass(holdingIdentity: HoldingIdentity, fullyQualifiedClassName: String) =
    this.get(holdingIdentity).sandboxGroup.loadClassFromMainBundles(fullyQualifiedClassName)

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * The [EntityRequest] contains the request and a typed payload.
 *
 * The [EntityResponse] contains the response or an exception-like payload whose presence indicates
 * an error has occurred.
 *
 * [payloadCheck] is called against each AMQP payload in the result (not the entire Avro array of results)
 */
class EntityMessageProcessor(
    private val entitySandboxService: EntitySandboxService,
    private val responseFactory: ResponseFactory,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer,
) : DurableProcessor<String, EntityRequest> {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MDC_EXTERNAL_EVENT_ID = "external_event_id"
    }

    override val keyClass = String::class.java

    override val valueClass = EntityRequest::class.java

    override fun onNext(events: List<Record<String, EntityRequest>>): List<Record<*, *>> {
        log.debug { "onNext processing messages ${events.joinToString(",") { it.key }}" }
        return events.mapNotNull { event ->
            val request = event.value
            if (request == null) {
                // We received a [null] external event therefore we do not know the flow id to respond to.
                return@mapNotNull null
            } else {
                withMDC(mapOf(MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId)) {
                    try {
                        val holdingIdentity = request.holdingIdentity.toCorda()
                        val sandbox = entitySandboxService.get(holdingIdentity)
                        processRequestWithSandbox(sandbox, request)
                    } catch (e: Exception) {
                        responseFactory.errorResponse(request.flowExternalEventContext, e)
                    }
                }
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun processRequestWithSandbox(
        sandbox: SandboxGroupContext,
        request: EntityRequest
    ): Record<String, FlowEvent> {
        val holdingIdentity = request.holdingIdentity.toCorda()

        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        val serializationService = sandbox.getSerializationService()

        val persistenceServiceInternal = PersistenceServiceInternal(entitySandboxService::getClass, payloadCheck)

        return entityManagerFactory.createEntityManager().transaction {
            when (val entityRequest = request.request) {
                is PersistEntities -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.persist(serializationService, it, entityRequest)
                )
                is DeleteEntities -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.deleteEntities(serializationService, it, entityRequest)
                )
                is DeleteEntitiesById -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.deleteEntitiesByIds(
                        serializationService,
                        it,
                        entityRequest,
                        holdingIdentity
                    )
                )
                is MergeEntities -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.merge(serializationService, it, entityRequest)
                )
                is FindEntities -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.find(serializationService, it, entityRequest, holdingIdentity)
                )
                is FindAll -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.findAll(serializationService, it, entityRequest, holdingIdentity)
                )
                is FindWithNamedQuery -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.findWithNamedQuery(serializationService, it, entityRequest)
                )
                else -> {
                    responseFactory.fatalErrorResponse(
                        request.flowExternalEventContext,
                        CordaRuntimeException("Unknown command")
                    )
                }
            }
        }
    }
}
