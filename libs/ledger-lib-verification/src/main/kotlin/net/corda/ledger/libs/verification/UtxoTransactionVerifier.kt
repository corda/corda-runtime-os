package net.corda.ledger.libs.verification

/**
 * This interface is used to verify an Utxo Ledger Transaction or a transaction builder.
 * It has a single method called [verify] which contains all the logic needed to properly
 * verify the transaction or the builder.
 */
interface UtxoTransactionVerifier {
    fun verify()
}
