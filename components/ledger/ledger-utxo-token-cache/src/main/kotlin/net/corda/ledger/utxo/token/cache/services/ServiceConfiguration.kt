package net.corda.ledger.utxo.token.cache.services

import net.corda.libs.configuration.SmartConfig

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
        // Need to create a constant for this config value in the API
        get() = config?.getInt("tokens.cachedTokenPageSize")
            ?:throw IllegalStateException("The token service has not been configured.")

}