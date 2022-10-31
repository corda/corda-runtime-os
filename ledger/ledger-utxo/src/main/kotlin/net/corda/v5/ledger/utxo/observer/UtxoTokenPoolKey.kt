package net.corda.v5.ledger.utxo.observer

import net.corda.v5.crypto.SecureHash

/**
 * The [UtxoTokenPoolKey] defines a key for a pool of similar tokens.
 *
 * The full token pool key includes Holding ID, token type, issue, notary and symbol. The platform provides the
 * holding ID, notary and optional token type.
 *
 * @property tokenType The type of token within a pool. If nothing is sepecified the platform will default this to the
 * class name of the contact state this key was created from by an implementation of [UtxoLedgerTokenStateObserver]
 * @property issuerHash The [SecureHash] of the issuer of the tokens in a pool.
 * @property symbol The user defined symbol of the tokens in a pool.
 */
data class UtxoTokenPoolKey(
    val tokenType: String?,
    val issuerHash: SecureHash,
    val symbol: String
) {
    constructor(
        issuerHash: SecureHash,
        symbol: String
    ) : this(null, issuerHash, symbol)
}
