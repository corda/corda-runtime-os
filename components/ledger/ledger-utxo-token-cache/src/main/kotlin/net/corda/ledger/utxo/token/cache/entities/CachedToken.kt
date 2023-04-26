package net.corda.ledger.utxo.token.cache.entities

import java.math.BigDecimal
import net.corda.data.ledger.utxo.token.selection.data.Token

/**
 * The [CachedToken] represents a token stored in the cache
 *
 * @property stateRef The unique reference for the token
 * @property amount The amount of the token
 * @property tag The optional user defined tag used to categorise the token
 * @property ownerHash The optional owner identity of the token
 */
interface CachedToken {
    val stateRef: String

    val amount: BigDecimal

    val tag: String

    val ownerHash: String

    /**
     * Creates an Avro representation of the token.
     *
     * @return The Avro representation of the token.
     */
    fun toAvro(): Token
}
