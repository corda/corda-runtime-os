package net.corda.v5.ledger.consensual.transaction

import net.corda.v5.base.annotations.Suspendable

fun interface ConsensualSignedTransactionVerifier {

    // TODO [CORE-7027] Document that this API should throw to fail verification
    @Suspendable
    fun verify(signedTransaction: ConsensualSignedTransaction)
}