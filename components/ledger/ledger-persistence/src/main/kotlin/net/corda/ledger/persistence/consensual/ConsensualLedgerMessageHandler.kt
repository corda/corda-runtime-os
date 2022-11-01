package net.corda.ledger.persistence.consensual

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.ledger.persistence.common.MessageHandler
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

@Suppress("LongParameterList")
class ConsensualLedgerMessageHandler(
    private val sandbox: SandboxGroupContext,
    private val request: LedgerPersistenceRequest,
    private val responseFactory: ResponseFactory,
    private val merkleTreeProvider: MerkleTreeProvider,
    private val digestService: DigestService,
    private val jsonMarshallingService: JsonMarshallingService
) : MessageHandler {

    private companion object {
        val log = contextLogger()
        const val CORDA_ACCOUNT = "corda.account"
    }

    @Suppress("ComplexMethod")
    override fun execute(): List<Record<String, FlowEvent>> {
        val holdingIdentity = request.holdingIdentity.toCorda()
        log.info("processRequestWithSandbox, request: $request, holding identity: $holdingIdentity")

        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        val serializationService = sandbox.getSerializationService()
        val repository = ConsensualLedgerRepository(
            merkleTreeProvider, digestService, jsonMarshallingService, serializationService
        )

        return listOf(entityManagerFactory.createEntityManager().transaction { em ->
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
                    responseFactory.fatalErrorResponse(
                        request.flowExternalEventContext,
                        CordaRuntimeException("Unknown command")
                    )
                }
            }
        })
    }

    private fun LedgerPersistenceRequest.account() =
        flowExternalEventContext.contextProperties.items.find { it.key == CORDA_ACCOUNT }?.value
            ?: throw NullParameterException("Flow external event context property '$CORDA_ACCOUNT' not set")

    private fun ConsensualSignedTransactionContainer.cpkMetadata() = wireTransaction.metadata.getCpkMetadata()

    private fun SerializationService.serialized(obj: Any) = ByteBuffer.wrap(serialize(obj).bytes)

    private fun SerializationService.deserialize(persistTransaction: PersistTransaction) =
        deserialize<ConsensualSignedTransactionContainer>(persistTransaction.transaction.array())
}