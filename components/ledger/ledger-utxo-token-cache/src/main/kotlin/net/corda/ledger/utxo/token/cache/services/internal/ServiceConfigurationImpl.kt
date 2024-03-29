package net.corda.ledger.utxo.token.cache.services.internal

import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.LedgerConfig.UTXO_TOKEN_CACHED_TOKEN_PAGE_SIZE
import net.corda.schema.configuration.LedgerConfig.UTXO_TOKEN_CACHE_REFRESH_PERIOD_MILLISECONDS
import net.corda.schema.configuration.LedgerConfig.UTXO_TOKEN_CLAIM_TIMEOUT_SECONDS
import org.osgi.service.component.annotations.Component
import java.time.Duration

@Component
class ServiceConfigurationImpl : ServiceConfiguration {

    private var config: SmartConfig? = null

    override fun init(config: SmartConfig) {
        this.config = config
    }

    override val cachedTokenPageSize: Int
        get() = getIntValue(UTXO_TOKEN_CACHED_TOKEN_PAGE_SIZE)

    override val claimTimeoutSeconds: Int
        get() = getIntValue(UTXO_TOKEN_CLAIM_TIMEOUT_SECONDS)

    override val tokenCacheExpiryPeriod: Duration
        get() = Duration.ofMillis(getLongValue(UTXO_TOKEN_CACHE_REFRESH_PERIOD_MILLISECONDS))

    private fun getIntValue(name: String): Int {
        return checkNotNull(config?.getInt(name)) { "The token service has not been configured, missing $name." }
    }

    private fun getLongValue(name: String): Long {
        return checkNotNull(config?.getLong(name)) { "The token service has not been configured, missing $name." }
    }
}
