package net.corda.ledger.utxo.token.cache.services

import net.corda.libs.configuration.SmartConfig
import kotlin.time.Duration

interface ServiceConfiguration {
    fun init(config: SmartConfig)

    val cachedTokenPageSize: Int

    val claimTimeoutSeconds: Int

    val tokenCacheExpiryPeriodMilliseconds: Duration
}
