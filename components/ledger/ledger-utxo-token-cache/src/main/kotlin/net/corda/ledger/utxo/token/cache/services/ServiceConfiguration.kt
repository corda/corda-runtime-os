package net.corda.ledger.utxo.token.cache.services

import net.corda.libs.configuration.SmartConfig
import java.time.Duration

interface ServiceConfiguration {
    fun init(config: SmartConfig)

    val cachedTokenPageSize: Int

    val claimTimeoutSeconds: Int

    val tokenCacheExpiryPeriod: Duration

    val dbTokensFetchMinPeriod: Duration

    val dbTokensFetchMaxPeriod: Duration
}
