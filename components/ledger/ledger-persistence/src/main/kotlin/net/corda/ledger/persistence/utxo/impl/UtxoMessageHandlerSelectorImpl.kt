package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.persistence.common.MessageHandler
import net.corda.ledger.persistence.utxo.UtxoMessageHandlerSelector
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [UtxoMessageHandlerSelector::class])
class UtxoMessageHandlerSelectorImpl @Activate constructor(
    @Reference(service = UtxoPersistenceService::class)
    private val utxoPersistanceService: UtxoPersistenceService,
    @Reference(service = UtxoOutputRecordFactory::class)
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : UtxoMessageHandlerSelector {

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): MessageHandler {
        when (val req = request.request) {
            is PersistTransaction -> {
                return UtxoPersistTransactionMessageHandler(
                    UtxoTransactionReaderImpl(sandbox, request.flowExternalEventContext, req),
                    UtxoTokenObserverMapImpl(sandbox),
                    request.flowExternalEventContext,
                    utxoPersistanceService,
                    utxoOutputRecordFactory
                )
            }
            else -> {
                throw IllegalStateException(" the UTXO request type '${request.request.javaClass}' is not supported.")
            }
        }
    }
}
