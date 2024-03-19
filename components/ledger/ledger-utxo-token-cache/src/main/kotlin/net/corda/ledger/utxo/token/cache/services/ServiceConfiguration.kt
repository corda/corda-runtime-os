package net.corda.ledger.utxo.token.cache.services

import net.corda.libs.configuration.SmartConfig

interface ServiceConfiguration {
    fun init(config: SmartConfig)

    val cachedTokenPageSize: Int

    val claimTimeoutSeconds: Int

    val tokenCacheExpiryPeriodMilliseconds: Long
}
