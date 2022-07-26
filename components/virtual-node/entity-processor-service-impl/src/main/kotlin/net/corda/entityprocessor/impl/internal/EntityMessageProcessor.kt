package net.corda.entityprocessor.impl.internal

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.event.external.ExternalEventResponseExceptionEnvelope
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.DeleteEntityById
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import net.corda.entityprocessor.impl.internal.exceptions.NotReadyException
import net.corda.entityprocessor.impl.internal.exceptions.VirtualNodeException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer
import javax.persistence.EntityManagerFactory

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
    private val clock: Clock,
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
                val response = try {
                    processRequest(request)
                } catch (e: Exception) {
                    // If we're catching at this point, it's an unrecoverable error.
                    errorResponse(event.key, e, ExternalEventResponseErrorType.FATAL_ERROR)
                }
                Record(
                    Schemas.Flow.FLOW_EVENT_TOPIC,
                    request.flowExternalEventContext.flowId,
                    FlowEvent(request.flowExternalEventContext.flowId, response)
                )
            }
        }
    }

    private fun processRequest(request: EntityRequest): ExternalEventResponse {
        val requestId = request.flowExternalEventContext.requestId
        val holdingIdentity = request.holdingIdentity.toCorda()
        // Get the sandbox for the given request.
        // Handle any exceptions as close to the throw-site as possible.
        val sandbox = try {
            entitySandboxService.get(holdingIdentity)
        } catch (e: NotReadyException) {
            // Flow worker could retry later, but may not be successful.
            return errorResponse(requestId, e, ExternalEventResponseErrorType.RETRY)
        } catch (e: VirtualNodeException) {
            // Flow worker could retry later, but may not be successful.
            return errorResponse(requestId, e, ExternalEventResponseErrorType.RETRY)
        } catch (e: Exception) {
            throw e // rethrow and handle higher up
        }

        return processRequestWithSandbox(sandbox, requestId, request)
    }

    @Suppress("ComplexMethod")
    private fun processRequestWithSandbox(
        sandbox: SandboxGroupContext,
        requestId: String,
        request: EntityRequest
    ): ExternalEventResponse {
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
                        requestId,
                        persistenceServiceInternal.persist(serializationService, it, entityRequest)
                    )
                    is DeleteEntity -> successResponse(
                        requestId,
                        persistenceServiceInternal.remove(serializationService, it, entityRequest)
                    )
                    is DeleteEntityById -> successResponse(
                        requestId,
                        persistenceServiceInternal.removeById(serializationService, it, entityRequest, holdingIdentity)
                    )
                    is MergeEntity -> successResponse(
                        requestId,
                        persistenceServiceInternal.merge(serializationService, it, entityRequest)
                    )
                    is FindEntity -> successResponse(
                        requestId,
                        persistenceServiceInternal.find(serializationService, it, entityRequest, holdingIdentity)
                    )
                    is FindAll -> successResponse(
                        requestId,
                        persistenceServiceInternal.findAll(serializationService, it, entityRequest, holdingIdentity)
                    )
                    else -> {
                        errorResponse(requestId, CordaRuntimeException("Unknown command"), ExternalEventResponseErrorType.FATAL_ERROR)
                    }
                }
            }
        } catch (e: java.io.NotSerializableException) {
            // Deserialization failure should be deterministic, and therefore retrying won't save you.
            // We mark this as FATAL so the flow worker knows about it, as per PR discussion.
            errorResponse(requestId, e, ExternalEventResponseErrorType.PLATFORM_ERROR)
        } catch (e: KafkaMessageSizeException) {
            // Results exceeded max packet size that we support at the moment.
            // We intend to support chunked results later.
            errorResponse(requestId, e, ExternalEventResponseErrorType.FATAL_ERROR)
        } catch (e: Exception) {
            errorResponse(requestId, e, ExternalEventResponseErrorType.RETRY)
        }

        return response
    }

    private fun successResponse(requestId: String, persistenceResult: PersistenceResult) : ExternalEventResponse {
        return ExternalEventResponse.newBuilder()
            .setRequestId(requestId)
            .setChunkNumber(null)
            .setNumberOfChunks(null)
            .setPayload(EntityResponse())
            .setData(persistenceResult.result)
            .setExceptionEnvelope(null)
            .setTimestamp(clock.instant())
            .build()
    }

    private fun errorResponse(requestId: String, e: Exception, errorType: ExternalEventResponseErrorType): ExternalEventResponse {
        if (errorType == ExternalEventResponseErrorType.FATAL_ERROR) {
            //  Fatal exception here is unrecoverable and serious as far the flow worker is concerned.
            log.error("Exception occurred (type=$errorType) for flow-worker request $requestId", e)
        } else {
            //  Non-fatal is a warning, and could be 'no cpks', but should still be logged for investigation.
            log.warn("Exception occurred (type=$errorType) for flow-worker request $requestId", e)
        }

        return ExternalEventResponse.newBuilder()
            .setRequestId(requestId)
            .setChunkNumber(null)
            .setNumberOfChunks(null)
            .setPayload(null)
            .setData(null)
            .setExceptionEnvelope(
                ExternalEventResponseExceptionEnvelope(
                    errorType,
                    // changed to [message] from [localizedMessage] to give a better error message
                    ExceptionEnvelope(e::class.java.simpleName, e.message)
                )
            )
            .setTimestamp(clock.instant())
            .build()
    }

    override val keyClass = String::class.java

    override val valueClass = EntityRequest::class.java
}
