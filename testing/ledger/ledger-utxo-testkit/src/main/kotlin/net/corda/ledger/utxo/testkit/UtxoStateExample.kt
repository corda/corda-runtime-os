package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

val utxoStateExample = UtxoStateClassExample("test", listOf(publicKeyExample))

val utxoInvalidStateExample = UtxoInvalidStateClassExample("test", listOf(publicKeyExample))

val utxoInvalidStateAndRefExample: StateAndRef<UtxoInvalidStateClassExample> = StateAndRefImpl(
    state = TransactionStateImpl(utxoInvalidStateExample, utxoNotaryExample, null),
    ref = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 1, 1)), 0)
)