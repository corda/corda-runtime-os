package net.corda.entityprocessor.impl.internal

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.virtualnode.DeleteEntity
import net.corda.data.virtualnode.EntityRequest
import net.corda.data.virtualnode.EntityResponse
import net.corda.data.virtualnode.FindEntity
import net.corda.data.virtualnode.MergeEntity
import net.corda.data.virtualnode.PersistEntity
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.schema.Schemas
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import java.time.Instant
import javax.persistence.EntityManagerFactory

/**
 * Handles incoming requests and sends responses.
 */
class EntityMessageProcessor(
    private val entitySandboxService: EntitySandboxService,
) : DurableProcessor<String, EntityRequest> {
    companion object {
        private val log = contextLogger()
    }

    private val persistenceServiceInternal = PersistenceServiceInternal(entitySandboxService)

    private fun SandboxGroupContext.getSerializationService(): SerializationService =
        getObjectByKey(EntitySandboxContextTypes.SANDBOX_SERIALIZER)
            ?: throw CordaRuntimeException("P2P serialization service not found within the sandbox for identity: " +
                    "${virtualNodeContext.holdingIdentity}")

    private fun SandboxGroupContext.getEntityManagerFactory(): EntityManagerFactory =
        getObjectByKey(EntitySandboxContextTypes.SANDBOX_EMF)
            ?: throw CordaRuntimeException("Entity manager factory not found within the sandbox for identity: " +
                    "${virtualNodeContext.holdingIdentity}")

    override fun onNext(events: List<Record<String, EntityRequest>>): List<Record<*, *>> {
        log.debug("onNext processing messages ${events.joinToString(",") { it.key }}")
        val responses = mutableListOf<Record<FlowKey, EntityResponse>>()
        events.forEach {
            try {
                val response = processRequest(it.key, it.value!!)
                responses.add(Record(Schemas.Flow.FLOW_EVENT_TOPIC, it.value!!.flowKey, response))
            } catch (e: Exception) {
                // TODO - allow a throw or not?  Matthew mentioned something about resubmitting/handling failure.
                responses.add(Record(Schemas.Flow.FLOW_EVENT_TOPIC, it.value!!.flowKey, exceptionResponse(it.key, e)))
            }
        }

        return responses
    }

    private fun processRequest(key: String, request: EntityRequest): EntityResponse {
        // Now get its sandbox
        val holdingIdentity = request.flowKey.identity.toCorda()
        val sandbox = entitySandboxService.get(holdingIdentity)

        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        val serializationService = sandbox.getSerializationService()

        // we match on the type, and pass the cast into the persistence service.
        val responseBytes = entityManagerFactory.createEntityManager().transaction {
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
                else -> throw CordaRuntimeException("Unexpected PersistenceService operation type: ${request.request}")
            }
        }

        return EntityResponse(Instant.now(), key, responseBytes)
    }

    private fun exceptionResponse(key: String, e: Exception) =
        EntityResponse(Instant.now(), key, ExceptionEnvelope(e::class.java.simpleName, e.localizedMessage))

    override val keyClass: Class<String>
        get() = String::class.java

    override val valueClass: Class<EntityRequest>
        get() = EntityRequest::class.java
}
