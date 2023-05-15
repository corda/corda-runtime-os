package com.r3.corda.notary.plugin.common

import net.corda.v5.base.annotations.CordaSerializable
import java.security.PublicKey

/**
 * A generic notary request payload that acts as a "base" class for multiple notarization payloads.
 * It runs validation that checks if the given [transaction]'s type is actually one of [validTypes].
 *
 * @property transaction The transaction object that needs notarizing
 * @property notaryKeys The notary service's key the client expects the signature from. This might
 * be a [CompositeKey][net.corda.v5.crypto.CompositeKey].
 * @property validTypes The transaction types that are accepted
 * (e.g. [UtxoSignedTransaction][net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction])
 */
@CordaSerializable
abstract class BaseNotarizationPayload(
    val transaction: Any,
    val notaryKey: PublicKey,
    private val validTypes: List<Class<*>>
) {

    init {
        require(validTypes.any { it.isAssignableFrom(transaction::class.java) }) {
            "Unexpected transaction type ${transaction::class.java} in " +
                    "notarization payload. There may be a mismatch " +
                    "between the configured notary type and the one " +
                    "advertised on the network parameters."
        }
    }
}
