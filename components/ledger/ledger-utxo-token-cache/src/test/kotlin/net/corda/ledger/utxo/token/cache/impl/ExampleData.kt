package net.corda.ledger.utxo.token.cache.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.libs.configuration.SmartConfigFactory

const val SECURE_HASH = "sh"

val POOL_CACHE_KEY =  TokenPoolCacheKey().apply {
    shortHolderId="h1"
    tokenType = "t1"
    issuerHash = SECURE_HASH
    notaryX500Name = "n"
    symbol = "s"
}

val POOL_CACHE_KEY_DTO = EntityConverterImpl().toTokenPoolKey(POOL_CACHE_KEY)

val MINIMUM_SMART_CONFIG = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
