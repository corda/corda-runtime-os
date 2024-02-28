package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

/**
 * Verify if the notary of a transaction is allowed based on the related group parameters.
 * It checks:
 * 1. whether the group parameters are referenced in the metadata of the transaction.
 * 2. if there are any notaries matching based on x500 names.
 * 3. if the key of that notary matches with the notary key of the transaction.
 *
 * It assumes that the signed group parameters have been verified earlier.
 *
 */
fun verifyNotaryAllowed(
    notaryName: MemberX500Name,
    notaryKey: PublicKey,
    metadata: TransactionMetadataInternal,
    signedGroupParameters: SignedGroupParameters
) {
    val allowedNotaries = signedGroupParameters.notaries

    val txGroupParametersHash = metadata.getMembershipGroupParametersHash()
    check(txGroupParametersHash == signedGroupParameters.hash.toString()) {
        "Membership group parameters (${signedGroupParameters.hash}) is not the one associated to the transaction " +
            " in its metadata ($txGroupParametersHash)."
    }

    val notaryCandidate = checkNotNull(allowedNotaries.singleOrNull { it.name == notaryName }) {
        "Notary of the transaction ($notaryName) is not listed in the available notaries."
    }
    check(notaryCandidate.publicKey == notaryKey) {
        "Notary key of the transaction (${notaryKey.publicKeyId()} is not matching against " +
            "the related notary (${notaryCandidate.publicKey.publicKeyId()} in Signed Group Parameters."
    }
}
