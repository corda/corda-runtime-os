package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonServices
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
                    UtxoPersistenceServiceImpl(sandbox,sandbox.getService(),sandbox.getService(), UTCClock()),
                    UtxoOutputRecordFactoryImpl()
                )
            }
            else -> {
                throw IllegalStateException(" the UTXO request type '${request.request.javaClass}' is not supported.")
            }
        }
    }

    /*
     val sandboxSingletons = sandbox.getSandboxSingletonServices()
        val repository = sandboxSingletons.filterIsInstance<ConsensualLedgerRepository>().singleOrNull()
            ?: throw IllegalStateException("ConsensualLedgerRepository service missing from sandbox")
     */
    inline fun <reified T> SandboxGroupContext.getService(): T {
        return this.getSandboxSingletonServices().filterIsInstance<T>().singleOrNull()
            ?: throw IllegalStateException("Could not find an instance of '${ T::class.java}' in the sandbox")
    }
}
