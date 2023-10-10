package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.converters.EventConverterImpl
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
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.TokenCacheEventProcessor
import net.corda.ledger.utxo.token.cache.services.internal.AvailableTokenServiceImpl
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList", "Unused")
@Component(service = [ TokenCacheEventProcessorFactory::class ])
class TokenCacheEventProcessorFactoryImpl @Activate constructor(
    @Reference
    private val serviceConfiguration: ServiceConfiguration,
    @Reference
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference
    private val dbConnectionManager: DbConnectionManager,
    @Reference
    private val jpaEntitiesRegistry: JpaEntitiesRegistry
) : TokenCacheEventProcessorFactory {

    override fun create(): StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent> {
        val entityConverter = EntityConverterImpl(serviceConfiguration, UTCClock())
        val eventConverter = EventConverterImpl(entityConverter)
        val recordFactory = RecordFactoryImpl(externalEventResponseFactory)
        val tokenFilterStrategy = SimpleTokenFilterStrategy()
        val sqlQueryProvider = SqlQueryProviderTokens()
        val utxoTokenRepository = UtxoTokenRepositoryImpl(sqlQueryProvider)
        val tokenPoolCache = TokenPoolCacheImpl()
        val availableTokenService = AvailableTokenServiceImpl(
            virtualNodeInfoService,
            dbConnectionManager,
            jpaEntitiesRegistry,
            utxoTokenRepository
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
        return TokenCacheEventProcessor(eventConverter, entityConverter, tokenPoolCache, eventHandlerMap, externalEventResponseFactory)
    }

    private inline fun <reified T : TokenEvent> createHandler(
        handler: TokenEventHandler<in T>
    ): Pair<Class<T>, TokenEventHandler<in TokenEvent>> {
        @Suppress("unchecked_cast")
        return Pair(T::class.java, handler as TokenEventHandler<in TokenEvent>)
    }
}
