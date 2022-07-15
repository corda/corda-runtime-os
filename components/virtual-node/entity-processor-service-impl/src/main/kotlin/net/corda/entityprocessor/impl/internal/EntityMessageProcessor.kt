package net.corda.entityprocessor.impl.internal

import java.nio.ByteBuffer
import javax.persistence.EntityManagerFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.DeleteEntityById
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import net.corda.entityprocessor.impl.internal.exceptions.NotReadyException
import net.corda.entityprocessor.impl.internal.exceptions.NullParameterException
import net.corda.entityprocessor.impl.internal.exceptions.VirtualNodeException
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda

fun EntitySandboxService.getClass(holdingIdentity: HoldingIdentity, fullyQualifiedClassName: String) =
    this.get(holdingIdentity).sandboxGroup.loadClassFromMainBundles(fullyQualifiedClassName)

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * The [EntityRequest] contains the request and a typed payload.
 *
 * The [EntityResponse] contains the response or an exception-like payload whose presence indicates
 * an error has occurred.
 */
class EntityMessageProcessor(
    private val entitySandboxService: EntitySandboxService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer,
) : DurableProcessor<String, EntityRequest> {
    companion object {
        private val log = contextLogger()
    }

    private fun SandboxGroupContext.getSerializationService(): SerializationService =
        getObjectByKey(EntitySandboxContextTypes.SANDBOX_SERIALIZER)
            ?: throw CordaRuntimeException(
                "Entity serialization service not found within the sandbox for identity: " +
                        "${virtualNodeContext.holdingIdentity}"
            )

    private fun SandboxGroupContext.getEntityManagerFactory(): EntityManagerFactory =
        getObjectByKey(EntitySandboxContextTypes.SANDBOX_EMF)
            ?: throw CordaRuntimeException(
                "Entity manager factory not found within the sandbox for identity: " +
                        "${virtualNodeContext.holdingIdentity}"
            )

    override fun onNext(events: List<Record<String, EntityRequest>>): List<Record<*, *>> {
        log.debug("onNext processing messages ${events.joinToString(",") { it.key }}")
        return events.mapNotNull { event ->
            val request = event.value
            if (request == null) {
                // We received a [null] external event therefore we do not know the flow id to respond to.
                return@mapNotNull null
            } else {
                try {
                    processRequest(request)
                } catch (e: Exception) {
                    // If we're catching at this point, it's an unrecoverable error.
                    fatalErrorResponse(request.flowExternalEventContext, e)
                }
            }
        }
    }

    private fun processRequest(request: EntityRequest): Record<String, FlowEvent> {
        val holdingIdentity = request.holdingIdentity.toCorda()
        // Get the sandbox for the given request.
        // Handle any exceptions as close to the throw-site as possible.
        val sandbox = try {
            entitySandboxService.get(holdingIdentity)
        } catch (e: NotReadyException) {
            // Flow worker could retry later, but may not be successful.
            return retriableErrorResponse(request.flowExternalEventContext, e)
        } catch (e: VirtualNodeException) {
            // Flow worker could retry later, but may not be successful.
            return retriableErrorResponse(request.flowExternalEventContext, e)
        } catch (e: Exception) {
            throw e // rethrow and handle higher up
        }

        return processRequestWithSandbox(sandbox, request)
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

        // We match on the type, and pass the cast into the persistence service.
        // Any exception that occurs next we assume originates in Hibernate and categorise
        // it accordingly.
        val response = try {
            entityManagerFactory.createEntityManager().transaction {
                when (val entityRequest = request.request) {
                    is PersistEntity -> successResponse(
                        request.flowExternalEventContext,
                        persistenceServiceInternal.persist(serializationService, it, entityRequest)
                    )
                    is DeleteEntity -> successResponse(
                        request.flowExternalEventContext,
                        persistenceServiceInternal.remove(serializationService, it, entityRequest)
                    )
                    is DeleteEntityById -> successResponse(
                        request.flowExternalEventContext,
                        persistenceServiceInternal.removeById(serializationService, it, entityRequest, holdingIdentity)
                    )
                    is MergeEntity -> successResponse(
                        request.flowExternalEventContext,
                        persistenceServiceInternal.merge(serializationService, it, entityRequest)
                    )
                    is FindEntity -> successResponse(
                        request.flowExternalEventContext,
                        persistenceServiceInternal.find(serializationService, it, entityRequest, holdingIdentity)
                    )
                    is FindAll -> successResponse(
                        request.flowExternalEventContext,
                        persistenceServiceInternal.findAll(serializationService, it, entityRequest, holdingIdentity)
                    )
                    is FindWithNamedQuery -> persistenceServiceInternal.findWithNamedQuery(
                        serializationService,
                        it,
                        request.request as FindWithNamedQuery
                    )
                    else -> {
                        fatalErrorResponse(request.flowExternalEventContext, CordaRuntimeException("Unknown command"))
                    }
                }
            }
        } catch (e: java.io.NotSerializableException) {
            // Deserialization failure should be deterministic, and therefore retrying won't save you.
            // We mark this as FATAL so the flow worker knows about it, as per PR discussion.
            platformErrorResponse(request.flowExternalEventContext, e)
        } catch (e: KafkaMessageSizeException) {
            // Results exceeded max packet size that we support at the moment.
            // We intend to support chunked results later.
            fatalErrorResponse(request.flowExternalEventContext, e)
        } catch (e: NullParameterException) {
            fatalErrorResponse(request.flowExternalEventContext, e)
        } catch (e: Exception) {
            retriableErrorResponse(request.flowExternalEventContext, e)
        }

        return response
    }

    private fun successResponse(
        flowExternalEventContext: ExternalEventContext,
        entityResponse: EntityResponse
    ): Record<String, FlowEvent> {
        return externalEventResponseFactory.success(flowExternalEventContext, entityResponse)
    }

    private fun retriableErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.warn(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.RETRY), e)
        return externalEventResponseFactory.retriable(flowExternalEventContext, e)
    }

    private fun platformErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.warn(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.PLATFORM_ERROR), e)
        return externalEventResponseFactory.platformError(flowExternalEventContext, e)
    }

    private fun fatalErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.error(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.FATAL_ERROR), e)
        return externalEventResponseFactory.fatalError(flowExternalEventContext, e)
    }

    private fun errorLogMessage(
        flowExternalEventContext: ExternalEventContext,
        errorType: ExternalEventResponseErrorType
    ): String {
        return "Exception occurred (type=$errorType) for flow-worker request ${flowExternalEventContext.requestId}"
    }

    override val keyClass = String::class.java

    override val valueClass = EntityRequest::class.java
}
