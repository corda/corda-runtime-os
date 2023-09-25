package net.corda.ledger.utxo.token.cache.entities

/**
 * The [TokenEvent] represents a received event for the token cache
 *
 * @property poolKey The key of the specific token pool the event is for
 */
interface TokenEvent {
    val externalEventRequestId: String
    val flowId: String
    val poolKey: TokenPoolKey
}
