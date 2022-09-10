package net.corda.processors.ledger.impl

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import java.nio.ByteBuffer
import javax.persistence.EntityManagerFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes
import net.corda.entityprocessor.impl.internal.EntitySandboxService
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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import net.corda.data.ledger.consensual.FindTransaction
import net.corda.data.persistence.ConsensualLedgerRequest
import net.corda.data.persistence.EntityResponse
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.PrivacySalt
import java.io.NotSerializableException


fun EntitySandboxService.getClass(holdingIdentity: HoldingIdentity, fullyQualifiedClassName: String) =
    this.get(holdingIdentity).sandboxGroup.loadClassFromMainBundles(fullyQualifiedClassName)

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * The [ConsensualLedgerRequest] contains the request and a typed payload.
 *
 * The [EntityResponse] contains the response or an exception-like payload whose presence indicates
 * an error has occurred.
 */

// TODO get rid of temporary JSON serialisation code now that an injectable AMQP serialiser is available
@JsonIgnoreProperties(ignoreUnknown = true)
data class MappablePrivacySalt @JsonCreator constructor(@JsonProperty override val bytes: ByteArray): PrivacySalt

@JsonIgnoreProperties(ignoreUnknown = true)
data class MappableWireTransaction @JsonCreator constructor(
    @JsonProperty val id: SecureHash,
    @JsonProperty val privacySalt: MappablePrivacySalt,
    @JsonProperty val componentGroupLists: List<List<ByteArray>>
)

class ConsensualLedgerProcessor(
    private val entitySandboxService: EntitySandboxService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer,
) : DurableProcessor<String, ConsensualLedgerRequest> {
    companion object {
        private val log = contextLogger()
        private val mapper = getMapper()

        private fun getMapper(): ObjectMapper {
            val mapper = jacksonObjectMapper()
            mapper.addMixIn(PrivacySalt::class.java, MappablePrivacySalt::class.java)
            return mapper
        }
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

    override fun onNext(events: List<Record<String, ConsensualLedgerRequest>>): List<Record<*, *>> {
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

    private fun processRequest(request: ConsensualLedgerRequest): Record<String, FlowEvent> {
        val holdingIdentity = request.holdingIdentity.toCorda()

        // Get the sandbox for the given request.
        // Handle any exceptions as close to the throw-site as possible.
        val sandbox = try {
            entitySandboxService.get(holdingIdentity)
        } catch (e: NotReadyException) {
            // Flow worker could retry later, but may not be successful.
            return transientErrorResponse(request.flowExternalEventContext, e)
        } catch (e: VirtualNodeException) {
            // Flow worker could retry later, but may not be successful.
            return transientErrorResponse(request.flowExternalEventContext, e)
        } catch (e: Exception) {
            throw e // rethrow and handle higher up
        }

        return processRequestWithSandbox(sandbox, request)
    }

    @Suppress("ComplexMethod")
    private fun processRequestWithSandbox(
        sandbox: SandboxGroupContext,
        request: ConsensualLedgerRequest
    ): Record<String, FlowEvent> {
        val holdingIdentity = request.holdingIdentity.toCorda()
        log.info("processRequestWithSandbox, request: ${request}, holding identity: ${holdingIdentity}")

        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        // val serializationService = sandbox.getSerializationService()  // TODO use
        val consensualLedgerDAO = ConsensualLedgerDAO(entitySandboxService::getClass)

        // We match on the type, and delegate to the appropriate method in the DAO.
        val response: Record<String, FlowEvent> = try {
            entityManagerFactory.createEntityManager().transaction {
                when (val req = request.request) {
                    is PersistTransaction -> successResponse(
                        request.flowExternalEventContext,
                        consensualLedgerDAO.persistTransaction(deserialize(req.transaction), it)
                    )

                    is FindTransaction -> successResponse(
                        request.flowExternalEventContext,
                        consensualLedgerDAO.findTransaction(req.id, it)
                    )

                    else -> {
                        fatalErrorResponse(request.flowExternalEventContext, CordaRuntimeException("Unknown command"))
                    }
                }
            }
        } catch (e: NotSerializableException) {
            fatalErrorResponse(request.flowExternalEventContext, e)
        } catch (e: KafkaMessageSizeException) {
            fatalErrorResponse(request.flowExternalEventContext, e)
        } catch (e: NullParameterException) {
            fatalErrorResponse(request.flowExternalEventContext, e)
        } catch (e: Exception) {
            fatalErrorResponse(request.flowExternalEventContext, e)
        }

        return response
    }

    /**
     * TODO Temporary JSON deserializer for transactions. We should replace this with AMQP-based serialization
     * but at the moment that isn't quite ready.
     */
    private fun deserialize(bytes: ByteBuffer): MappableWireTransaction {
        val inStream = ByteBufferBackedInputStream(bytes)
        return mapper.readValue(inStream, jacksonTypeRef<MappableWireTransaction>())
    }

    private fun successResponse(
        flowExternalEventContext: ExternalEventContext,
        entityResponse: EntityResponse
    ): Record<String, FlowEvent> {
        return externalEventResponseFactory.success(flowExternalEventContext, entityResponse)
    }

    private fun transientErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.warn("Error encountered while handling flow event ${flowExternalEventContext.requestId}", e)
        return externalEventResponseFactory.transientError(flowExternalEventContext, e)
    }

    private fun fatalErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.error("Fatal error encountered while handling flow event ${flowExternalEventContext.requestId}", e)
        return externalEventResponseFactory.fatalError(flowExternalEventContext, e)
    }

    override val keyClass: Class<String>
        get() = String::class.java

    override val valueClass: Class<ConsensualLedgerRequest>
        get() = ConsensualLedgerRequest::class.java
}
