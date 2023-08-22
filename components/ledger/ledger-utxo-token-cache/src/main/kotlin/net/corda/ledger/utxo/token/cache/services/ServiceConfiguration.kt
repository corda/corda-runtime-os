package net.corda.ledger.utxo.token.cache.services

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.LedgerConfig.UTXO_TOKEN_CACHED_TOKEN_PAGE_SIZE

interface ServiceConfiguration {
    fun init(config: SmartConfig)

    val cachedTokenPageSize: Int
}
