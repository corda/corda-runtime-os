package net.corda.ledger.utxo.impl

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef

/**
 * Represents input and output states that are grouped by a key that is common to all inputs and outputs.
 *
 * @constructor Creates a new instance of the [InputOutputGroupImpl] data class.
 * @param T The underlying type of the grouped [ContractState] instances.
 * @param K The underlying type of the grouping key.
 * @property inputs The inputs that are grouped by the specified grouping key.
 * @property outputs The outputs that are grouped by the specified grouping key.
 * @property groupingKey The grouping key that is common to all grouped input and output states.
 */
@CordaSerializable
data class InputOutputGroupImpl<out T : ContractState, out K : Any>(
    val inputs: List<StateAndRef<T>>,
    val outputs: List<StateAndRef<T>>,
    val groupingKey: K
)
