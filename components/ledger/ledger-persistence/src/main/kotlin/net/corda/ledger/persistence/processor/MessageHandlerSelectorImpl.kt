package net.corda.ledger.persistence.processor

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.ledger.persistence.common.MessageHandler
import net.corda.ledger.persistence.consensual.ConsensualLedgerMessageHandler
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MessageHandlerSelector::class])
class MessageHandlerSelectorImpl @Activate constructor(
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : MessageHandlerSelector {

    companion object {
        val log = contextLogger()
    }

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): MessageHandler {
        when (request.ledgerType) {
            LedgerTypes.CONSENSUAL -> {
                return ConsensualLedgerMessageHandler(
                    sandbox,
                    request,
                    responseFactory,
                    merkleTreeProvider,
                    digestService,
                    jsonMarshallingService
                )
            }

            LedgerTypes.UTXO -> {
                TODO()
            }
            else -> {
                val error = IllegalStateException("unsupported ledger type '${request.ledgerType}'")
                log.error(error.message)
                throw error
            }
        }
    }
}