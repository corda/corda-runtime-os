package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ledger.persistence.FindSignedGroupParameters
import net.corda.data.ledger.persistence.FindSignedLedgerTransaction
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.FindTransactionIdsAndStatuses
import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.PersistSignedGroupParametersIfDoNotExist
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.ledger.persistence.PersistTransactionIfDoesNotExist
import net.corda.data.ledger.persistence.ResolveStateRefs
import net.corda.data.ledger.persistence.UpdateTransactionStatus
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.common.UnsupportedRequestTypeException
import net.corda.ledger.persistence.json.impl.DefaultContractStateVaultJsonFactoryImpl
import net.corda.ledger.persistence.query.execution.impl.VaultNamedQueryExecutorImpl
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoExecuteNamedQueryHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoFindSignedGroupParametersRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoFindSignedLedgerTransactionRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoFindTransactionIdsAndStatusesRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoFindTransactionRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoFindUnconsumedStatesByTypeRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoPersistSignedGroupParametersIfDoNotExistRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoPersistTransactionIfDoesNotExistRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoPersistTransactionRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoResolveStateRefsRequestHandler
import net.corda.ledger.persistence.utxo.impl.request.handlers.UtxoUpdateTransactionStatusRequestHandler
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
) : UtxoRequestHandlerSelector {

    @Suppress("LongMethod")
    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler {
        val persistenceService = UtxoPersistenceServiceImpl(
            entityManagerFactory = sandbox.getEntityManagerFactory(),
            repository = sandbox.getSandboxSingletonService(),
            serializationService = sandbox.getSerializationService(),
            sandboxDigestService = sandbox.getSandboxSingletonService(),
            factoryStorage = sandbox.getSandboxSingletonService(),
            defaultContractStateVaultJsonFactory = DefaultContractStateVaultJsonFactoryImpl(),
            jsonMarshallingService = sandbox.getSandboxSingletonService(),
            UTCClock()
        )

        val vaultNamedQueryExecutor = VaultNamedQueryExecutorImpl(
            sandbox.getEntityManagerFactory(),
            sandbox.getSandboxSingletonService(),
            sandbox.getSerializationService()
        )

        val serializationService = sandbox.getSerializationService()
        val outputRecordFactory = UtxoOutputRecordFactoryImpl(responseFactory, serializationService)
        val externalEventContext = request.flowExternalEventContext

        return when (val req = request.request) {
            is FindTransaction -> {
                UtxoFindTransactionRequestHandler(
                    req,
                    externalEventContext,
                    persistenceService,
                    outputRecordFactory
                )
            }
            is FindSignedLedgerTransaction -> {
                UtxoFindSignedLedgerTransactionRequestHandler(
                    req,
                    externalEventContext,
                    persistenceService,
                    outputRecordFactory
                )
            }
            is FindUnconsumedStatesByType -> {
                UtxoFindUnconsumedStatesByTypeRequestHandler(
                    req,
                    sandbox,
                    externalEventContext,
                    persistenceService,
                    outputRecordFactory
                )
            }
            is ResolveStateRefs -> {
                UtxoResolveStateRefsRequestHandler(
                    req,
                    externalEventContext,
                    persistenceService,
                    outputRecordFactory
                )
            }
            is PersistTransaction -> {
                UtxoPersistTransactionRequestHandler(
                    UtxoTransactionReaderImpl(sandbox, externalEventContext, req),
                    UtxoTokenObserverMapImpl(sandbox),
                    externalEventContext,
                    persistenceService,
                    outputRecordFactory,
                    sandbox.getSandboxSingletonService()
                )
            }
            is PersistTransactionIfDoesNotExist -> {
                UtxoPersistTransactionIfDoesNotExistRequestHandler(
                    UtxoTransactionReaderImpl(sandbox, externalEventContext, req),
                    externalEventContext,
                    externalEventResponseFactory,
                    serializationService,
                    persistenceService
                )
            }
            is UpdateTransactionStatus -> {
                UtxoUpdateTransactionStatusRequestHandler(
                    req,
                    externalEventContext,
                    externalEventResponseFactory,
                    persistenceService
                )
            }
            is FindWithNamedQuery -> {
                UtxoExecuteNamedQueryHandler(
                    externalEventContext,
                    req,
                    vaultNamedQueryExecutor,
                    externalEventResponseFactory
                )
            }
            is FindSignedGroupParameters -> {
                UtxoFindSignedGroupParametersRequestHandler(
                    req,
                    externalEventContext,
                    persistenceService,
                    responseFactory
                )
            }
            is PersistSignedGroupParametersIfDoNotExist -> {
                UtxoPersistSignedGroupParametersIfDoNotExistRequestHandler(
                    req,
                    externalEventContext,
                    externalEventResponseFactory,
                    persistenceService
                )
            }
            is FindTransactionIdsAndStatuses -> {
                UtxoFindTransactionIdsAndStatusesRequestHandler(
                    req,
                    externalEventContext,
                    persistenceService,
                    externalEventResponseFactory,
                    serializationService
                )
            }
            else -> {
                throw UnsupportedRequestTypeException(LedgerTypes.UTXO, request.request.javaClass)
            }
        }
    }
}
