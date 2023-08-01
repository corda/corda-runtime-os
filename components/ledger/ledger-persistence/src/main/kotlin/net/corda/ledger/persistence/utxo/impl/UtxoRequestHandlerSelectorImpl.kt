package net.corda.ledger.persistence.utxo.impl

import net.corda.data.ledger.persistence.FindSignedGroupParameters
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.PersistSignedGroupParametersIfDoNotExist
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.ledger.persistence.PersistTransactionIfDoesNotExist
import net.corda.data.ledger.persistence.ResolveStateRefs
import net.corda.data.ledger.persistence.UpdateTransactionStatus
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.common.UnsupportedRequestTypeException
import net.corda.ledger.persistence.json.impl.DefaultContractStateVaultJsonFactoryImpl
import net.corda.ledger.persistence.query.execution.impl.VaultNamedQueryExecutorImpl
import net.corda.ledger.persistence.utxo.UtxoRequestHandlerSelector
import net.corda.ledger.utxo.datamodel.UtxoLedgerEntities
import net.corda.orm.JpaEntitiesRegistry
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
    private val responseFactory: ResponseFactory,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
): UtxoRequestHandlerSelector {

    init {
        jpaEntitiesRegistry.register(
            CordaDb.Vault.persistenceUnitName,
            emptySet()
        )
    }

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler {
        val persistenceService = UtxoPersistenceServiceImpl(
            entityManagerFactory = dbConnectionManager.getOrCreateEntityManagerFactory(
                VirtualNodeDbType.VAULT.getSchemaName(sandbox.virtualNodeContext.holdingIdentity.shortHash),
                DbPrivilege.DML,
                entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                    ?: throw IllegalStateException(
                        "persistenceUnitName " +
                                "${CordaDb.Vault.persistenceUnitName} is not registered."
                    )
            ),
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
            is ResolveStateRefs -> {
                return UtxoResolveStateRefsRequestHandler(
                    req,
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
                    UtxoOutputRecordFactoryImpl(responseFactory),
                    sandbox.getSandboxSingletonService()
                )
            }
            is PersistTransactionIfDoesNotExist -> {
                UtxoPersistTransactionIfDoesNotExistRequestHandler(
                    UtxoTransactionReaderImpl(sandbox, request.flowExternalEventContext, req),
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
            is FindWithNamedQuery -> {
                UtxoExecuteNamedQueryHandler(
                    request.flowExternalEventContext,
                    req,
                    vaultNamedQueryExecutor,
                    externalEventResponseFactory
                )
            }
            is FindSignedGroupParameters -> {
                return UtxoFindSignedGroupParametersRequestHandler(
                    req,
                    request.flowExternalEventContext,
                    persistenceService,
                    responseFactory
                )
            }
            is PersistSignedGroupParametersIfDoNotExist -> {
                UtxoPersistSignedGroupParametersIfDoNotExistRequestHandler(
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
