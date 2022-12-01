package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash

interface TransactionBackchainVerifier {

    // shall we throw an exception or return a boolean?? Or a result object, but that is basically an exception :)
    @Suspendable
    fun verify(resolvingTransactionId: SecureHash, topologicalSort: TopologicalSort): Boolean
}