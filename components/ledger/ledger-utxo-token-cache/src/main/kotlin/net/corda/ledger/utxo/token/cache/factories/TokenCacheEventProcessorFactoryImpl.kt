package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
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
import net.corda.ledger.utxo.token.cache.services.TokenCacheEventProcessor
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheStateSerialization
import net.corda.ledger.utxo.token.cache.services.TokenSelectionDelegatedProcessor
import net.corda.ledger.utxo.token.cache.services.TokenSelectionDelegatedProcessorImpl
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetricsImpl
import net.corda.ledger.utxo.token.cache.services.internal.AvailableTokenServiceImpl
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.read.VirtualNodeInfoReadService

@Suppress("LongParameterList")
class TokenCacheEventProcessorFactoryImpl constructor(
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

    private val tokenPoolCache = TokenPoolCacheImpl()

    override fun create(): StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent> {
        val recordFactory = RecordFactoryImpl(externalEventResponseFactory)
        val tokenFilterStrategy = SimpleTokenFilterStrategy()
        val sqlQueryProvider = SqlQueryProviderTokens()
        val utxoTokenRepository = UtxoTokenRepositoryImpl(sqlQueryProvider)
        val tokenSelectionMetrics = TokenSelectionMetricsImpl(clock)
        val availableTokenService = AvailableTokenServiceImpl(
            virtualNodeInfoService,
            dbConnectionManager,
            jpaEntitiesRegistry,
            utxoTokenRepository,
            tokenSelectionMetrics
        )

        val eventHandlerMap = mapOf<Class<*>, TokenEventHandler<in TokenEvent>>(
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
        return TokenCacheEventProcessor(
            eventConverter,
            entityConverter,
            tokenPoolCache,
            eventHandlerMap,
            externalEventResponseFactory
        )
    }

    override fun createDelegatedProcessor(
        stateManager: StateManager,
        processor: StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>
    ): TokenSelectionDelegatedProcessor {
        val claimStateStoreFactory = ClaimStateStoreFactoryImpl(stateManager, serialization, tokenPoolCache, clock)
        val tokenSelectionMetrics = TokenSelectionMetricsImpl(UTCClock())
        return TokenSelectionDelegatedProcessorImpl(
            eventConverter,
            entityConverter,
            create(),
            ClaimStateStoreCacheImpl(stateManager, serialization, claimStateStoreFactory, clock),
            externalEventResponseFactory,
            tokenSelectionMetrics
        )
    }

    private inline fun <reified T : TokenEvent> createHandler(
        handler: TokenEventHandler<in T>
    ): Pair<Class<T>, TokenEventHandler<in TokenEvent>> {
        @Suppress("unchecked_cast")
        return Pair(T::class.java, handler as TokenEventHandler<in TokenEvent>)
    }
}
