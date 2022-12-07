package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash

interface TransactionBackchainVerifier {

    @Suspendable
    fun verify(resolvingTransactionId: SecureHash, topologicalSort: TopologicalSort): Boolean
}