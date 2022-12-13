package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.ledger.utxo.ContractVerificationFailure
import net.corda.v5.ledger.utxo.StateAndRef
import java.lang.IllegalArgumentException

@Suppress("NestedBlockDepth")
fun verifyEncumberedInput(inputStateAndRefs: List<StateAndRef<*>>): List<ContractVerificationFailure> {
    val failureReasons = mutableListOf<ContractVerificationFailure>()

    // group input by transaction id (encumbrance is only unique within one transaction output)
    inputStateAndRefs.groupBy { it.ref.transactionHash }.forEach { statesByTx ->
        // Filter out unencumbered states
        statesByTx.value.filter { it.state.encumbrance != null }
            // within each tx, group by encumbrance tag, store the output index and the encumbrance group size
            .groupBy({ it.state.encumbrance!!.tag }, { Pair(it.ref.index, it.state.encumbrance!!.size) })
            // for each output entry, run the checks
            .forEach { encumbranceGroup ->
                // Check that no input states have been duplicated to fool our counting
                encumbranceGroup.value.groupBy { it.first }.filter { it.value.size > 1 }.apply{
                    this.forEach{ failureReasons.add(
                        ContractVerificationFailureImpl(
                            contractClassName = "",
                            contractStateClassNames = emptyList(),
                            exceptionClassName = IllegalArgumentException::class.java.canonicalName,
                            exceptionMessage = "Encumbrance check failed: State ${statesByTx.key}, " +
                                    "${it.key} is part " +
                                    "is used ${it.value.size} times as input!"))
                    }
                }

                val numberOfStatesPresent = encumbranceGroup.value.size
                // if the size of the encumbrance group does not match the number of input states,
                // then add a failure reason.
                encumbranceGroup.value.forEach { encumbrancePair ->
                    if (encumbrancePair.second != numberOfStatesPresent) {
                        failureReasons.add(
                            ContractVerificationFailureImpl(
                                contractClassName = "",
                                contractStateClassNames = emptyList(),
                                exceptionClassName = IllegalArgumentException::class.java.canonicalName,
                                exceptionMessage = "Encumbrance check failed: State ${statesByTx.key}, " +
                                        "${encumbrancePair.first} is part " +
                                        "of encumbrance group ${encumbranceGroup.key}, but only " +
                                        "$numberOfStatesPresent states out of " +
                                        "${encumbrancePair.second} encumbered states are present as inputs."

                            )
                        )
                    }
                }
            }
    }
    return failureReasons
}
