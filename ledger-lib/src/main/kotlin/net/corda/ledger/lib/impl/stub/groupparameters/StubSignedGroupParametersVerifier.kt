package net.corda.ledger.lib.impl.stub.groupparameters

import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class StubSignedGroupParametersVerifier : SignedGroupParametersVerifier {
    override fun verify(
        transaction: UtxoLedgerTransaction,
        signedGroupParameters: net.corda.membership.lib.SignedGroupParameters?
    ) {}

    override fun verifySignature(signedGroupParameters: net.corda.membership.lib.SignedGroupParameters) {}

}