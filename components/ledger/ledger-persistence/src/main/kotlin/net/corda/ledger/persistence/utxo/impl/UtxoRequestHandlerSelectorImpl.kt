package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [UtxoRequestHandlerSelector::class])
class UtxoRequestHandlerSelectorImpl : UtxoRequestHandlerSelector {

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler {
        when (val req = request.request) {
            is PersistTransaction -> {
                return UtxoPersistTransactionRequestHandler(
                    UtxoTransactionReaderImpl(sandbox, request.flowExternalEventContext, req),
                    UtxoTokenObserverMapImpl(sandbox),
                    request.flowExternalEventContext,
                    UtxoPersistenceServiceImpl(
                        sandbox,
                        UtxoRepositoryImpl(),
                        sandbox.getSandboxSingletonService(),
                        UTCClock()
                    ),
                    UtxoOutputRecordFactoryImpl()
                )
            }
            else -> {
                throw IllegalStateException(" the UTXO request type '${request.request.javaClass}' is not supported.")
            }
        }
    }
}
