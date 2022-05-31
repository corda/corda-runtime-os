package net.corda.entityprocessor.impl.internal

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.persistence.DeleteEntityById
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseFailure
import net.corda.data.persistence.Error
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
        val responses = mutableListOf<Record<FlowKey, EntityResponse>>()
        events.forEach {
            val response = try {
                processRequest(it.key, it.value!!)
            } catch (e: Exception) {
                // If we're catching at this point, it's an unrecoverable error.
                failureResponse(it.key, e, Error.FATAL)
            }
            responses.add(Record(Schemas.Flow.FLOW_EVENT_TOPIC, it.value!!.flowKey, response))
        }

        return responses
    }

    private fun processRequest(requestId: String, request: EntityRequest): EntityResponse {
        val holdingIdentity = request.flowKey.identity.toCorda()

        // Get the sandbox for the given request.
        // Handle any exceptions as close to the throw-site as possible.
        val sandbox = try {
            entitySandboxService.get(holdingIdentity)
        } catch (e: NotReadyException) {
            // Flow worker could retry later, but may not be successful.
            return failureResponse(requestId, e, Error.NOT_READY)
        } catch (e: VirtualNodeException) {
            // Flow worker could retry later, but may not be successful.
            return failureResponse(requestId, e, Error.VIRTUAL_NODE)
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
    ): EntityResponse {
        val holdingIdentity = request.flowKey.identity.toCorda()

        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        val serializationService = sandbox.getSerializationService()

        val persistenceServiceInternal = PersistenceServiceInternal(
            entitySandboxService::getClass,
            requestId,
            clock,
            payloadCheck
        )

        // We match on the type, and pass the cast into the persistence service.
        // Any exception that occurs next we assume originates in Hibernate and categorise
        // it accordingly.
        val response = try {
            entityManagerFactory.createEntityManager().transaction {
                when (request.request) {
                    is PersistEntity -> persistenceServiceInternal.persist(
                        serializationService,
                        it,
                        request.request as PersistEntity
                    )
                    is DeleteEntity -> persistenceServiceInternal.remove(
                        serializationService,
                        it,
                        request.request as DeleteEntity
                    )
                    is DeleteEntityById -> persistenceServiceInternal.removeById(
                        serializationService,
                        it,
                        request.request as DeleteEntityById,
                        holdingIdentity
                    )
                    is MergeEntity -> persistenceServiceInternal.merge(
                        serializationService,
                        it,
                        request.request as MergeEntity
                    )
                    is FindEntity -> persistenceServiceInternal.find(
                        serializationService,
                        it,
                        request.request as FindEntity,
                        holdingIdentity
                    )
                    is FindAll -> persistenceServiceInternal.findAll(
                        serializationService,
                        it,
                        request.request as FindAll,
                        holdingIdentity
                    )
                    else -> {
                        failureResponse(requestId, CordaRuntimeException("Unknown command"), Error.FATAL)
                    }
                }
            }
        } catch (e: java.io.NotSerializableException) {
            // Deserialization failure should be deterministic, and therefore retrying won't save you.
            // We mark this as FATAL so the flow worker knows about it, as per PR discussion.
            failureResponse(requestId, e, Error.FATAL)
        } catch (e: KafkaMessageSizeException) {
            // Results exceeded max packet size that we support at the moment.
            // We intend to support chunked results later.
            failureResponse(requestId, e, Error.FATAL)
        } catch (e: Exception) {
            failureResponse(requestId, e, Error.DATABASE)
        }

        return response
    }

    private fun failureResponse(requestId: String, e: Exception, errorType: Error): EntityResponse {
        if (errorType == Error.FATAL) {
            //  Fatal exception here is unrecoverable and serious as far the flow worker is concerned.
            log.error("Exception occurred (type=$errorType) for flow-worker request $requestId", e)
        } else {
            //  Non-fatal is a warning, and could be 'no cpks', but should still be logged for investigation.
            log.warn("Exception occurred (type=$errorType) for flow-worker request $requestId", e)
        }

        return EntityResponse(
            clock.instant(),
            requestId,
            EntityResponseFailure(
                errorType,
                ExceptionEnvelope(e::class.java.simpleName, e.localizedMessage)
            )
        )
    }

    override val keyClass: Class<String>
        get() = String::class.java

    override val valueClass: Class<EntityRequest>
        get() = EntityRequest::class.java
}
