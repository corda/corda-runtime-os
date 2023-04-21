package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

/**
 * The [TokenEvent] represents a received event for the token cache
 *
 * @property poolKey The key of the specific token pool the event is for
 */
interface TokenEvent {
    val poolKey: TokenPoolCacheKey
}
