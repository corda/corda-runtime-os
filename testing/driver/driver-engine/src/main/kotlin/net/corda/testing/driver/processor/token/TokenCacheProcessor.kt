package net.corda.testing.driver.processor.token

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.messaging.api.records.Record
import net.corda.testing.driver.processor.ExternalProcessor
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [ TokenCacheProcessor::class ])
class TokenCacheProcessor : ExternalProcessor {
    private val logger = LoggerFactory.getLogger(TokenCacheProcessor::class.java)

    override fun processEvent(record: Record<*, *>): List<Record<*, *>> {
        val tokenPoolCacheKey = requireNotNull(record.key as? TokenPoolCacheKey) {
            "Invalid or missing TokenPoolCacheKey"
        }
        val tokenPoolCacheEvent = requireNotNull(record.value as? TokenPoolCacheEvent) {
            "Invalid or missing TokenPoolCacheEvent"
        }

        // Currently logging these only for information purposes.
        logger.info("Key={}, Event={}", tokenPoolCacheKey, tokenPoolCacheEvent)
        return emptyList()
    }
}
