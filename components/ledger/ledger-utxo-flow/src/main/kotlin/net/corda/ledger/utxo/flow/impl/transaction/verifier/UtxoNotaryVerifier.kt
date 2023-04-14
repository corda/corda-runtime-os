package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

fun verifyNotaryAllowed(transaction: UtxoLedgerTransaction, signedGroupParameters: SignedGroupParameters) {
    val allowedNotaries = signedGroupParameters.notaries

    val txGroupParametersHash = (transaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()
    check(txGroupParametersHash == signedGroupParameters.hash.toString()) {
        "Membership group parameters (${signedGroupParameters.hash}) is not the one associated to the transaction " +
                " in its metadata ($txGroupParametersHash)."
    }

    checkNotNull(
        allowedNotaries.firstOrNull { it.publicKey == transaction.notaryKey }
            ?: allowedNotaries.firstOrNull { it.name == transaction.notaryName }
    ) {
        "Notary of the transaction is not listed in the available notaries."
    }
}
