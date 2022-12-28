package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.ledger.persistence.PersistTransactionIfDoesNotExist
import net.corda.data.ledger.persistence.UpdateTransactionStatus
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.common.UnsupportedRequestTypeException
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getEntityManagerFactory
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
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
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
            sandbox.getEntityManagerFactory(),
            repository,
            sandbox.getSandboxSingletonService(),
            sandbox.getSandboxSingletonService(),
            UTCClock()
        )
        return when (val req = request.request) {
            is FindTransaction -> {
                return UtxoFindTransactionRequestHandler(
                    req,
                    sandbox.getSerializationService(),
                    request.flowExternalEventContext,
                    persistenceService,
                    UtxoOutputRecordFactoryImpl(responseFactory)
                )
            }
            is FindUnconsumedStatesByType -> {
                return UtxoFindUnconsumedStatesByTypeRequestHandler(
                    req,
                    sandbox,
                    sandbox.getSerializationService(),
                    request.flowExternalEventContext,
                    persistenceService,
                    UtxoOutputRecordFactoryImpl(responseFactory)
                )
            }
            is PersistTransaction -> {
                UtxoPersistTransactionRequestHandler(
                    sandbox.virtualNodeContext.holdingIdentity,
                    UtxoTransactionReaderImpl(sandbox, request.flowExternalEventContext, req),
                    UtxoTokenObserverMapImpl(sandbox),
                    request.flowExternalEventContext,
                    persistenceService,
                    UtxoOutputRecordFactoryImpl(responseFactory)
                )
            }
            is PersistTransactionIfDoesNotExist -> {
                UtxoPersistTransactionIfDoesNotExistRequestHandler(
                    req,
                    request.flowExternalEventContext,
                    externalEventResponseFactory,
                    sandbox.getSandboxSingletonService(),
                    persistenceService
                )
            }
            is UpdateTransactionStatus -> {
                UtxoUpdateTransactionStatusRequestHandler(
                    req,
                    request.flowExternalEventContext,
                    externalEventResponseFactory,
                    persistenceService
                )
            }
            else -> {
                throw UnsupportedRequestTypeException(LedgerTypes.UTXO, request.request.javaClass)
            }
        }
    }
}
