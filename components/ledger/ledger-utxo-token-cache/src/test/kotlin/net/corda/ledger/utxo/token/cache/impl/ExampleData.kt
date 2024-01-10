package net.corda.ledger.utxo.token.cache.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.libs.configuration.SmartConfigFactory

const val SECURE_HASH = "sh"

val POOL_CACHE_KEY = TokenPoolCacheKey().apply {
    shortHolderId = "h1"
    tokenType = "t1"
    issuerHash = SECURE_HASH
    notaryX500Name = "n"
    symbol = "s"
}

val POOL_KEY = TokenPoolKey(
    shortHolderId = "h1",
    tokenType = "t1",
    issuerHash = SECURE_HASH,
    notaryX500Name = "n",
    symbol = "s"
)

val TOKEN_POOL_CACHE_STATE: TokenPoolCacheState = TokenPoolCacheState.newBuilder()
    .setPoolKey(POOL_KEY.toAvro())
    .setAvailableTokens(listOf())
    .setTokenClaims(listOf())
    .build()

val TOKEN_CLAIM: TokenClaim = TokenClaim.newBuilder()
    .setClaimId("c1")
    .setClaimedTokens(listOf())
    .setClaimedTokenStateRefs(listOf())
    .setClaimTimestamp(0)
    .build()

val TOKEN_POOL_CACHE_STATE_2: TokenPoolCacheState = TokenPoolCacheState.newBuilder()
    .setPoolKey(POOL_KEY.toAvro())
    .setAvailableTokens(listOf())
    .setTokenClaims(listOf(TOKEN_CLAIM))
    .build()

val MINIMUM_SMART_CONFIG = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
