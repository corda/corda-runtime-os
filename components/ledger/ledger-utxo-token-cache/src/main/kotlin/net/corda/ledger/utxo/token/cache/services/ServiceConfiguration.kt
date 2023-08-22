package net.corda.ledger.utxo.token.cache.services

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.LedgerConfig.UTXO_TOKEN_CACHED_TOKEN_PAGE_SIZE

interface ServiceConfiguration {
    fun init(config: SmartConfig)

    val cachedTokenPageSize: Int
}

class ServiceConfigurationImpl : ServiceConfiguration {

    private var config: SmartConfig? = null

    override fun init(config: SmartConfig) {
        this.config = config
    }

    override val cachedTokenPageSize: Int
        get() = config?.getInt(UTXO_TOKEN_CACHED_TOKEN_PAGE_SIZE)
            ?:throw IllegalStateException("The token service has not been configured.")

}
