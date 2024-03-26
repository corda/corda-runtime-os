package net.corda.ledger.utxo.token.cache.factories

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.internal.TokenPoolCacheImpl
import net.corda.ledger.utxo.token.cache.handlers.TokenBalanceQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenForceClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenLedgerChangeEventHandler
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens
import net.corda.ledger.utxo.token.cache.repositories.impl.UtxoTokenRepositoryImpl
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreCacheImpl
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreFactoryImpl
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheManager
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheStateSerialization
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetrics
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetricsImpl
import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncRPCProcessor
import net.corda.ledger.utxo.token.cache.services.internal.AvailableTokenServiceImpl
import net.corda.libs.statemanager.api.StateManager
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService

@Suppress("LongParameterList")
class TokenCacheEventProcessorFactoryImpl(
    private val serviceConfiguration: ServiceConfiguration,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val entityConverter: EntityConverter,
    private val eventConverter: EventConverter,
    private val serialization: TokenPoolCacheStateSerialization,
    private val clock: Clock
) : TokenCacheEventProcessorFactory {

    override fun createTokenSelectionSyncRPCProcessor(
        stateManager: StateManager
    ): TokenSelectionSyncRPCProcessor {
        val tokenSelectionMetrics = TokenSelectionMetricsImpl()
        // For now the duration will be zero so the cache never expires.
        val tokenPoolCacheManager = TokenPoolCacheManager(
            TokenPoolCacheImpl(serviceConfiguration.tokenCacheExpiryPeriodMilliseconds),
            createEventHandlerMap(tokenSelectionMetrics)
        )
        val claimStateStoreFactory = ClaimStateStoreFactoryImpl(stateManager, serialization, tokenPoolCacheManager, clock)

        return TokenSelectionSyncRPCProcessor(
            eventConverter,
            entityConverter,
            tokenPoolCacheManager,
            ClaimStateStoreCacheImpl(claimStateStoreFactory),
            externalEventResponseFactory,
            tokenSelectionMetrics
        )
    }

    private fun createEventHandlerMap(tokenSelectionMetrics: TokenSelectionMetrics): Map<Class<*>, TokenEventHandler<in TokenEvent>> {
        val recordFactory = RecordFactoryImpl(externalEventResponseFactory)
        val tokenFilterStrategy = SimpleTokenFilterStrategy()
        val utxoTokenRepository = UtxoTokenRepositoryImpl(SqlQueryProviderTokens())

        val availableTokenService = AvailableTokenServiceImpl(
            virtualNodeInfoService,
            dbConnectionManager,
            jpaEntitiesRegistry,
            utxoTokenRepository,
            tokenSelectionMetrics
        )

        return mapOf(
            createHandler(
                TokenClaimQueryEventHandler(
                    tokenFilterStrategy,
                    recordFactory,
                    availableTokenService,
                    serviceConfiguration
                )
            ),
            createHandler(TokenClaimReleaseEventHandler(recordFactory)),
            createHandler(TokenForceClaimReleaseEventHandler()),
            createHandler(TokenLedgerChangeEventHandler()),
            createHandler(TokenBalanceQueryEventHandler(recordFactory, availableTokenService)),
        )
    }

    private inline fun <reified T : TokenEvent> createHandler(
        handler: TokenEventHandler<in T>
    ): Pair<Class<T>, TokenEventHandler<in TokenEvent>> {
        @Suppress("unchecked_cast")
        return Pair(T::class.java, handler as TokenEventHandler<in TokenEvent>)
    }
}
