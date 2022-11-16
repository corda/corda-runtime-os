package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [UtxoRequestHandlerSelector::class])
class UtxoRequestHandlerSelectorImpl @Activate constructor(
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory
): UtxoRequestHandlerSelector {

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler {
        val repository =  UtxoRepositoryImpl(
            sandbox.getSandboxSingletonService(),
            sandbox.getSandboxSingletonService(),
            sandbox.getSandboxSingletonService()
        )
        val persistenceService = UtxoPersistenceServiceImpl(
            sandbox,
            repository,
            sandbox.getSandboxSingletonService(),
            UTCClock()
        )
        when (val req = request.request) {
            is FindTransaction -> {
                return UtxoFindTransactionRequestHandler(
                    req,
                    sandbox.getSerializationService(),
                    request.flowExternalEventContext,
                    persistenceService,
                    responseFactory
                )
            }
            is PersistTransaction -> {
                return UtxoPersistTransactionRequestHandler(
                    UtxoTransactionReaderImpl(sandbox, request.flowExternalEventContext, req),
                    UtxoTokenObserverMapImpl(sandbox),
                    request.flowExternalEventContext,
                    persistenceService,
                    responseFactory
                )
            }
            else -> {
                throw IllegalStateException("The UTXO request type '${request.request.javaClass}' is not supported.")
            }
        }
    }
}
