package net.corda.ledger.utxo.testkit

import net.corda.ledger.utxo.impl.state.StateAndRefImpl
import net.corda.ledger.utxo.impl.state.TransactionStateImpl
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef


fun getUtxoInvalidStateAndRef(): StateAndRefImpl<UtxoStateClassExample> {
    val contractState = utxoStateExample
    val stateRef = StateRef(SecureHash("SHA-256", ByteArray(12)), 0)
    val transactionState = TransactionStateImpl(contractState, utxoNotaryExample, null)
    return StateAndRefImpl(transactionState, stateRef)
}