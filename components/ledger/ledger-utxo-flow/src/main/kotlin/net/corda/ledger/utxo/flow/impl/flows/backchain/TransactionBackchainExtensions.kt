package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

// Probably can't use [toLedgerTransaction] realistically since we need the backchain to create a valid ledger transaction in the
// first place. Might want a factory for ledger transaction and we can do resolution during the [create] method.
val UtxoSignedTransaction.dependencies: Set<SecureHash>
    get() = toLedgerTransaction()
        .let { it.inputStateRefs.asSequence() + it.referenceInputStateRefs.asSequence() }
        .map { it.transactionHash }
        .toSet()