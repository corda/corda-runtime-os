package net.corda.ledger.consensual.persistence.impl.processor

import net.corda.common.json.validation.JsonValidator
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.consensual.FindTransaction
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.ConsensualLedgerRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.ledger.consensual.persistence.impl.repository.ConsensualLedgerRepository
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * [payloadCheck] is called against each AMQP payload in the result (not the entire Avro array of results)
 */
@Suppress("LongParameterList")
class ConsensualLedgerMessageProcessor(
    private val entitySandboxService: EntitySandboxService,
    externalEventResponseFactory: ExternalEventResponseFactory,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer
) : DurableProcessor<String, ConsensualLedgerRequest> {
    private companion object {
        val log = contextLogger()
        const val CORDA_ACCOUNT = "corda.account"
    }

    private val responseFactory = ResponseFactory(externalEventResponseFactory, log)

    override val keyClass = String::class.java

    override val valueClass = ConsensualLedgerRequest::class.java

    override fun onNext(events: List<Record<String, ConsensualLedgerRequest>>): List<Record<*, *>> {
        log.debug { "onNext processing messages ${events.joinToString(",") { it.key }}" }
        return events.mapNotNull { event ->
            val request = event.value
            if (request == null) {
                // We received a [null] external event therefore we do not know the flow id to respond to.
                return@mapNotNull null
            } else {
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
        val repository = sandbox.getSandboxSingletonService<ConsensualLedgerRepository>()

        return entityManagerFactory.createEntityManager().transaction { em ->
            when (val req = request.request) {
                is PersistTransaction -> {
                    val transaction = serializationService.deserialize(req)
                    repository.persistTransaction(em, transaction, req.status, request.account())
                    val cpkMetadata = transaction.cpkMetadata()
                    val missingCpks = if (repository.persistTransactionCpk(em, transaction) < cpkMetadata.size) {
                        val persistedCpks = repository.findTransactionCpkChecksums(em, transaction)
                        cpkMetadata.filterNot { persistedCpks.contains(it.fileChecksum) }
                    } else {
                        emptyList()
                    }
                    responseFactory.successResponse(
                        request.flowExternalEventContext,
                        EntityResponse(missingCpks.map { serializationService.serialized(it) })
                    )
                }

                is FindTransaction -> responseFactory.successResponse(
                    request.flowExternalEventContext,
                    EntityResponse(
                        listOfNotNull(repository.findTransaction(em, req.id))
                            .map { serializationService.serialized(it) }
                    ))

                else -> {
                    responseFactory.fatalErrorResponse(request.flowExternalEventContext, CordaRuntimeException("Unknown command"))
                }
            }
        }
    }

    private fun ConsensualLedgerRequest.account() =
        flowExternalEventContext.contextProperties.items.find { it.key == CORDA_ACCOUNT }?.value
            ?: throw NullParameterException("Flow external event context property '$CORDA_ACCOUNT' not set")

    private fun ConsensualSignedTransactionContainer.cpkMetadata() = wireTransaction.metadata.getCpkMetadata()

    private fun SerializationService.serialized(obj: Any) = ByteBuffer.wrap(serialize(obj).bytes)

    private fun SerializationService.deserialize(persistTransaction: PersistTransaction) =
        deserialize<ConsensualSignedTransactionContainer>(persistTransaction.transaction.array())
}
