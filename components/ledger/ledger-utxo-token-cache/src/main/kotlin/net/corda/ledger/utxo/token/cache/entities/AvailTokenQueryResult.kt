package net.corda.ledger.utxo.token.cache.entities

data class AvailTokenQueryResult(val poolKey: TokenPoolKey, val tokens: Collection<CachedToken>)
