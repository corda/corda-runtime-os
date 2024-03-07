package net.corda.ledger.utxo.token.cache.services.internal

import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.LedgerConfig.UTXO_TOKEN_CACHED_TOKEN_PAGE_SIZE
import net.corda.schema.configuration.LedgerConfig.UTXO_TOKEN_CLAIM_TIMEOUT_SECONDS
import org.osgi.service.component.annotations.Component

@Component
class ServiceConfigurationImpl : ServiceConfiguration {

    private var config: SmartConfig? = null

    override fun init(config: SmartConfig) {
        this.config = config
    }

    override val cachedTokenPageSize: Int
        get() = getIntValue(UTXO_TOKEN_CACHED_TOKEN_PAGE_SIZE)

    override val claimTimeoutSeconds: Int
        get() = 600000//getIntValue(UTXO_TOKEN_CLAIM_TIMEOUT_SECONDS)

    private fun getIntValue(name: String): Int {
        return checkNotNull(config?.getInt(name)) { "The token service has not been configured, missing $name." }
    }
}
