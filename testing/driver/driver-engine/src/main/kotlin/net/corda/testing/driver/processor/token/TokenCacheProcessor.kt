package net.corda.testing.driver.processor.token

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.factories.TokenCacheEventProcessorFactory
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.testing.driver.config.SmartConfigProvider
import net.corda.testing.driver.processor.ExternalProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ TokenCacheProcessor::class ])
class TokenCacheProcessor @Activate constructor(
    @Reference
    tokenCacheEventProcessorFactory: TokenCacheEventProcessorFactory,
    @Reference
    serviceConfiguration: ServiceConfiguration,
    @Reference
    smartConfigProvider: SmartConfigProvider,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : ExternalProcessor {
    private val anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    private val processor = tokenCacheEventProcessorFactory.create()
    private var states = mutableMapOf<TokenPoolCacheKey, TokenPoolCacheState>()

    init {
        serviceConfiguration.init(smartConfigProvider.smartConfig)
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
}
