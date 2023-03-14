package net.corda.ledger.utxo.testkit

import net.corda.crypto.core.toByteArray
import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.v5.ledger.utxo.StateRef

fun getUtxoStateExample(testField: String = "test") = UtxoStateClassExample(testField, listOf(publicKeyExample))

private fun getUtxoInvalidStateExample(testField: String = "test") =
    UtxoInvalidStateClassExample(testField, listOf(publicKeyExample))

fun getExampleStateAndRefImpl(seed: Int = 1, testField: String = "test") = StateAndRefImpl(
    state = TransactionStateImpl(getUtxoStateExample(testField), utxoNotaryExample, null),
    ref = StateRef(SecureHashImpl("SHA", seed.toByteArray()), 0)
)

fun getExampleInvalidStateAndRefImpl(seed: Int = 1, testField: String = "test") = StateAndRefImpl(
    state = TransactionStateImpl(getUtxoInvalidStateExample(testField), utxoNotaryExample, null),
    ref = StateRef(SecureHashImpl("SHA", seed.toByteArray()), 0)
)