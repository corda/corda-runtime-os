package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

val UtxoSignedTransaction.dependencies: Set<SecureHash>
    get() = this
        .let { it.inputStateRefs.asSequence() + it.referenceStateRefs.asSequence() }
        .map { it.transactionId }
        .toSet()