package net.corda.ledger.utxo.transaction.verifier

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey

/*
 * Verify Signed Group parameters consistency. (hash and MGM signature.)
 */
interface SignedGroupParametersVerifier {
    /* Verify hash matching in transaction and signature */
    fun verify(
        transaction: UtxoLedgerTransaction,
        signedGroupParameters: SignedGroupParameters?,
        mgmPublicKeys: List<PublicKey>
    )

    fun verifySignature(
        signedGroupParameters: SignedGroupParameters,
        mgmPublicKeys: List<PublicKey>
    )

}
