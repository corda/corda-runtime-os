package net.corda.ledger.persistence.processor

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.ledger.persistence.common.MessageHandler
import net.corda.ledger.persistence.common.MessageHandlerSelector
import net.corda.ledger.persistence.consensual.ConsensualMessageHandlerSelector
import net.corda.ledger.persistence.utxo.UtxoMessageHandlerSelector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MessageHandlerSelector::class])
class MessageHandlerSelectorImpl @Activate constructor(
    @Reference(service = ConsensualMessageHandlerSelector::class)
    private val consensualMessageHandlerSelector: ConsensualMessageHandlerSelector,
    @Reference(service = UtxoMessageHandlerSelector::class)
    private val utxoMessageHandlerSelector: UtxoMessageHandlerSelector,
) : MessageHandlerSelector {

    companion object {
        val log = contextLogger()
    }

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): MessageHandler {
        when (request.ledgerType) {
            LedgerTypes.CONSENSUAL -> {
                return consensualMessageHandlerSelector.selectHandler(
                    sandbox,
                    request
                )
            }

            LedgerTypes.UTXO -> {
                return utxoMessageHandlerSelector.selectHandler(
                    sandbox,
                    request
                )
            }
            else -> {
                val error = IllegalStateException("unsupported ledger type '${request.ledgerType}'")
                log.error(error.message)
                throw error
            }
        }
    }
}
