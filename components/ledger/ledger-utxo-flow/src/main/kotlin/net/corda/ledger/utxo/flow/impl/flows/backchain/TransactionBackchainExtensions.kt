package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@Suspendable
val UtxoSignedTransaction.dependencies: Set<SecureHash>
    get() = toLedgerTransaction()
        .let { it.inputStateRefs.asSequence() + it.referenceInputStateRefs.asSequence() }
        .map { it.transactionHash }
        .toSet()