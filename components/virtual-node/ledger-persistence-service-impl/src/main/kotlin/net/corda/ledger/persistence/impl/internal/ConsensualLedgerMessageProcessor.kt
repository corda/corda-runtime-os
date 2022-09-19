package net.corda.ledger.persistence.impl.internal

import java.nio.ByteBuffer
import javax.persistence.EntityManagerFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.ledger.consensual.FindTransaction
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.ConsensualLedgerRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.persistence.common.exceptions.NotReadyException
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.persistence.common.exceptions.VirtualNodeException
import net.corda.persistence.common.EntitySandboxContextTypes
import net.corda.persistence.common.EntitySandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.serialization.SerializedBytes
import net.corda.virtualnode.toCorda

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * [payloadCheck] is called against each AMQP payload in the result (not the entire Avro array of results)
 */
class ConsensualLedgerMessageProcessor(
    private val entitySandboxService: EntitySandboxService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer,
) : DurableProcessor<String, ConsensualLedgerRequest> {
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
        log.info("processRequestWithSandbox, request: $request, holding identity: $holdingIdentity")

        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        val serializationService = sandbox.getSerializationService()
        val consensualLedgerDAO = ConsensualLedgerDao(merkleTreeFactory, digestService)

        // We match on the type, and pass the cast into the persistence service.
        // Any exception that occurs next we assume originates in Hibernate and categorise
        // it accordingly.
        val response = try {
            entityManagerFactory.createEntityManager().transaction {
                when (val req = request.request) {
                    is PersistTransaction -> successResponse(
                        request.flowExternalEventContext,
                        consensualLedgerDAO.persistTransaction(serializationService.deserialize(req), it)
                    )

                    is FindTransaction -> successResponse(
                        request.flowExternalEventContext,
                        createEntityResponse(
                            serializationService.serialize(
                                consensualLedgerDAO.findTransaction(req.id, it)
                            )
                        ))
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
            transientErrorResponse(request.flowExternalEventContext, e)
        }

        return response
    }

    private fun createEntityResponse(obj: SerializedBytes<Any>) = EntityResponse(listOf(ByteBuffer.wrap(obj.bytes)))
    private fun SerializationService.deserialize(persistTransaction: PersistTransaction) =
        deserialize(persistTransaction.transaction.array(), WireTransaction::class.java)

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
        log.warn(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.TRANSIENT), e)
        return externalEventResponseFactory.transientError(flowExternalEventContext, e)
    }

    private fun platformErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.warn(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.PLATFORM), e)
        return externalEventResponseFactory.platformError(flowExternalEventContext, e)
    }

    private fun fatalErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.error(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.FATAL), e)
        return externalEventResponseFactory.fatalError(flowExternalEventContext, e)
    }

    private fun errorLogMessage(
        flowExternalEventContext: ExternalEventContext,
        errorType: ExternalEventResponseErrorType
    ): String {
        return "Exception occurred (type=$errorType) for flow-worker request ${flowExternalEventContext.requestId}"
    }

    override val keyClass = String::class.java

    override val valueClass = ConsensualLedgerRequest::class.java
}
