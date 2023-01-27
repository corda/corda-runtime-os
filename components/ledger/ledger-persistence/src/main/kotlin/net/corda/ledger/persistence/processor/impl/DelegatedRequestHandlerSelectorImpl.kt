package net.corda.ledger.persistence.processor.impl

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.common.UnsupportedLedgerTypeException
import net.corda.ledger.persistence.consensual.ConsensualRequestHandlerSelector
import net.corda.ledger.persistence.processor.DelegatedRequestHandlerSelector
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [DelegatedRequestHandlerSelector::class])
class DelegatedRequestHandlerSelectorImpl @Activate constructor(
    @Reference(service = ConsensualRequestHandlerSelector::class)
    private val consensualMessageHandlerSelector: ConsensualRequestHandlerSelector,
    @Reference(service = UtxoRequestHandlerSelector::class)
    private val utxoRequestHandlerSelector: UtxoRequestHandlerSelector,
) : DelegatedRequestHandlerSelector {

    companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
                val error = UnsupportedLedgerTypeException(request.ledgerType)
                log.error(error.message)
                throw error
            }
        }
    }
}
