package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.transaction.ContractVerificationFailureImpl
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractVerificationFailure
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

fun verifyEncumbrance(transaction: UtxoLedgerTransaction): List<ContractVerificationFailure> {

    val failureReasons = mutableListOf<ContractVerificationFailure>()

    // group input by transaction id (encumbrance is only unique within one transaction output)
    transaction.inputStateAndRefs.groupBy { it.ref.transactionId }.forEach { statesByTx ->


        // Filter out unencumbered states
        val encumbranceGroups = statesByTx.value.filter { it.state.encumbrance != null }
            // within each tx, group by encumbrance tag, store the output index and the encumbrance group size
            .groupBy({ it.state.encumbrance!!.tag }, { EncumbranceInfo(it.ref.index, it.state.encumbrance!!.size) })

        // for each encumbrance group (identified by tx id/tag), run the checks
        encumbranceGroups.forEach { encumbranceGroup ->
            failureReasons.addAll(checkEncumbranceGroup(statesByTx.key, encumbranceGroup.key, encumbranceGroup.value))
        }
    }
    return failureReasons
}

private fun checkEncumbranceGroup(
    txId: SecureHash,
    encumbranceTag: String,
    stateInfos: List<EncumbranceInfo>
): List<ContractVerificationFailure> {
    // Check that no input states have been duplicated to fool our counting
    val duplicationFailures = stateInfos
        .groupBy { it.stateIndex }
        .filter { it.value.size > 1 }
        .map { (index, infos) ->
            ContractVerificationFailureImpl(
                contractClassName = "",
                contractStateClassNames = emptyList(),
                exceptionClassName = IllegalArgumentException::class.java.canonicalName,
                exceptionMessage = "Encumbrance check failed: State $txId, $index " +
                        "is used ${infos.size} times as input!"
            )
        }

    if (duplicationFailures.isNotEmpty()) {
        return duplicationFailures
    }

    val numberOfStatesPresent = stateInfos.size
    // if the size of the encumbrance group does not match the number of input states,
    // then add a failure reason.
    return stateInfos.mapNotNull { encumbranceInfo ->
        if (encumbranceInfo.encumbranceGroupSize != numberOfStatesPresent) {
            ContractVerificationFailureImpl(
                contractClassName = "",
                contractStateClassNames = emptyList(),
                exceptionClassName = IllegalArgumentException::class.java.canonicalName,
                exceptionMessage = "Encumbrance check failed: State $txId, " +
                        "${encumbranceInfo.stateIndex} is part " +
                        "of encumbrance group $encumbranceTag, but only " +
                        "$numberOfStatesPresent states out of " +
                        "${encumbranceInfo.encumbranceGroupSize} encumbered states are present as inputs."
            )
        } else {
            null
        }
    }
}

private data class EncumbranceInfo(val stateIndex: Int, val encumbranceGroupSize: Int)
