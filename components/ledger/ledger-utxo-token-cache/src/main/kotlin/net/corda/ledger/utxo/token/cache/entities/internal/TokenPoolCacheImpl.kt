package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import org.slf4j.LoggerFactory

class TokenPoolCacheImpl : TokenPoolCache {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val cache = mutableMapOf<TokenPoolKey, TokenCache>()

    override fun get(poolKey: TokenPoolKey): TokenCache {
        return cache.getOrPut(poolKey) {
            logger.info("Creating available token cache for '${poolKey}'")
            TokenCacheImpl()
        }
    }
}
