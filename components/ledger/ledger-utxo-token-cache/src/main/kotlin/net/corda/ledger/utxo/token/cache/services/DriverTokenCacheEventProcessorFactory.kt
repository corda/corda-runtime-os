package net.corda.ledger.utxo.token.cache.services

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.converters.EventConverterImpl
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.internal.TokenPoolCacheImpl
import net.corda.ledger.utxo.token.cache.factories.RecordFactoryImpl
import net.corda.ledger.utxo.token.cache.handlers.TokenBalanceQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenLedgerChangeEventHandler
import net.corda.ledger.utxo.token.cache.queries.impl.SqlQueryProviderTokens
import net.corda.ledger.utxo.token.cache.repositories.impl.UtxoTokenRepositoryImpl
import net.corda.ledger.utxo.token.cache.services.internal.AvailableTokenServiceImpl
import net.corda.ledger.utxo.token.cache.services.internal.ServiceConfigurationImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This component only exists to create a [TokenCacheEventProcessor] instance
 * for the driver. Ideally, it should not be needed in the first place.
 */
@Component(
    service = [ DriverTokenCacheEventProcessorFactory::class ],
    property = [ "corda.driver:Boolean=true" ]
)
class DriverTokenCacheEventProcessorFactory @Activate constructor(
    @Reference
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference
    private val dbConnectionManager: DbConnectionManager,
    @Reference
    private val jpaEntitiesRegistry: JpaEntitiesRegistry
) {
    fun create(smartConfig: SmartConfig): TokenCacheEventProcessor {
        val entityConverter = EntityConverterImpl()
        val eventConverter = EventConverterImpl(entityConverter)
        val recordFactory = RecordFactoryImpl(externalEventResponseFactory)
        val tokenFilterStrategy = SimpleTokenFilterStrategy()
        val sqlQueryProvider = SqlQueryProviderTokens()
        val utxoTokenRepository = UtxoTokenRepositoryImpl(sqlQueryProvider)
        val serviceConfiguration = ServiceConfigurationImpl().also { svc ->
            svc.init(smartConfig)
        }

        val availableTokenService = AvailableTokenServiceImpl(
            virtualNodeInfoService,
            dbConnectionManager,
            jpaEntitiesRegistry,
            utxoTokenRepository,
            serviceConfiguration
        )

        val eventHandlerMap = mapOf<Class<*>, TokenEventHandler<TokenEvent>>(
            createHandler(TokenClaimQueryEventHandler(tokenFilterStrategy, recordFactory, availableTokenService)),
            createHandler(TokenClaimReleaseEventHandler(recordFactory)),
            createHandler(TokenLedgerChangeEventHandler()),
            createHandler(TokenBalanceQueryEventHandler(recordFactory, availableTokenService)),
        )

        return TokenCacheEventProcessor(eventConverter, entityConverter, TokenPoolCacheImpl(), eventHandlerMap)
    }

    private inline fun <reified T : TokenEvent> createHandler(
        handler: TokenEventHandler<T>
    ): Pair<Class<T>, TokenEventHandler<TokenEvent>> {
        @Suppress("unchecked_cast")
        return Pair(T::class.java, handler as TokenEventHandler<TokenEvent>)
    }
}
