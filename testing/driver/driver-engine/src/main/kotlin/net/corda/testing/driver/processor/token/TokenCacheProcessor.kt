package net.corda.testing.driver.processor.token

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.converters.EventConverterImpl
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.factories.RecordFactoryImpl
import net.corda.ledger.utxo.token.cache.handlers.TokenBalanceQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimQueryEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenLedgerChangeEventHandler
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.ledger.utxo.token.cache.services.TokenCacheEventProcessor
import net.corda.testing.driver.processor.ExternalProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ TokenCacheProcessor::class ])
class TokenCacheProcessor @Activate constructor(
    @Reference
    availableTokenService: AvailableTokenService,
    @Reference
    externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : ExternalProcessor {
    private val anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    private var states = mutableMapOf<TokenPoolCacheKey, TokenPoolCacheState>()
    private val processor: TokenCacheEventProcessor

    init {
        val entityConverter = EntityConverterImpl()
        val eventConverter = EventConverterImpl(entityConverter)
        val recordFactory = RecordFactoryImpl(externalEventResponseFactory)
        val tokenFilterStrategy = SimpleTokenFilterStrategy()

        val eventHandlerMap = mapOf<Class<*>, TokenEventHandler<TokenEvent>>(
            createHandler(TokenClaimQueryEventHandler(tokenFilterStrategy, recordFactory, availableTokenService)),
            createHandler(TokenClaimReleaseEventHandler(recordFactory)),
            createHandler(TokenLedgerChangeEventHandler()),
            createHandler(TokenBalanceQueryEventHandler(recordFactory, availableTokenService)),
        )

        processor = TokenCacheEventProcessor(eventConverter, entityConverter, eventHandlerMap)
    }

    override fun processEvent(record: Record<*, *>): List<Record<*, *>> {
        val cacheKey = requireNotNull(unpack<TokenPoolCacheKey>(record.key)) {
            "Invalid or missing TokenPoolCacheKey"
        }
        val cacheEvent = requireNotNull(unpack<TokenPoolCacheEvent>(record.value)) {
            "Invalid or missing TokenPoolCacheEvent"
        }

        val response = processor.onNext(states[cacheKey], Record(record.topic, cacheKey, cacheEvent))
        val state = response.updatedState
        if (state != null) {
            states[cacheKey] = state
        } else {
            states.remove(cacheKey)
        }
        return response.responseEvents
    }

    private inline fun <reified T : Any> unpack(obj: Any?): T? {
        return (((obj as? ByteArray)?.let(anyDeserializer::deserialize)) ?: obj) as? T
    }

    private inline fun <reified T : TokenEvent> createHandler(
        handler: TokenEventHandler<T>
    ): Pair<Class<T>, TokenEventHandler<TokenEvent>> {
        @Suppress("unchecked_cast")
        return Pair(T::class.java, handler as TokenEventHandler<TokenEvent>)
    }
}
