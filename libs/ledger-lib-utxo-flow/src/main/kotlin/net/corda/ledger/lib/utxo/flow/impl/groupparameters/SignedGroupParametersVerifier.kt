package net.corda.ledger.lib.utxo.flow.impl.groupparameters

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/*
 * Verify Signed Group parameters consistency. (hash and MGM signature.)
 */
interface SignedGroupParametersVerifier {
    /* Verify hash matching in transaction and signature */
    /* todo CORE-15320 converge Signed and Ledger Transactions with a shared interface */
    fun verify(
        transaction: UtxoLedgerTransaction,
        signedGroupParameters: SignedGroupParameters?
    )

    fun verifySignature(
        signedGroupParameters: SignedGroupParameters
    )
}
