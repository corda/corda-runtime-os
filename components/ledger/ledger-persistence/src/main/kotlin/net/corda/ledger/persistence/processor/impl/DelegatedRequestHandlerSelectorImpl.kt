package net.corda.ledger.persistence.processor.impl

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.consensual.ConsensualRequestHandlerSelector
import net.corda.ledger.persistence.processor.DelegatedRequestHandlerSelector
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DelegatedRequestHandlerSelector::class])
class DelegatedRequestHandlerSelectorImpl @Activate constructor(
    @Reference(service = ConsensualRequestHandlerSelector::class)
    private val consensualMessageHandlerSelector: ConsensualRequestHandlerSelector,
    @Reference(service = UtxoRequestHandlerSelector::class)
    private val utxoRequestHandlerSelector: UtxoRequestHandlerSelector,
) : DelegatedRequestHandlerSelector {

    companion object {
        val log = contextLogger()
    }

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler {
        when (request.ledgerType) {
            LedgerTypes.CONSENSUAL -> {
                return consensualMessageHandlerSelector.selectHandler(
                    sandbox,
                    request
                )
            }

            LedgerTypes.UTXO -> {
                return utxoRequestHandlerSelector.selectHandler(
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
