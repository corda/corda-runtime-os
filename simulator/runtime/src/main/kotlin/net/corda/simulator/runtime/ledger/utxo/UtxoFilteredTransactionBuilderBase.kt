package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import java.security.PublicKey
import java.util.function.Predicate

@Suppress("TooManyFunctions")
data class UtxoFilteredTransactionBuilderBase(
    private val signedTransaction: UtxoSignedTransactionBase,
    val notary: Boolean = false,
    val timeWindow: Boolean = false,
    val commands: FilterParams? = null,
    val signatories: FilterParams? = null,
    val inputStates: FilterParams? = null,
    val referenceStates: FilterParams? = null,
    val outputStates: FilterParams? = null,
): UtxoFilteredTransactionBuilder {

    override fun withNotary(): UtxoFilteredTransactionBuilder {
        return copy(notary = true)
    }

    override fun withTimeWindow(): UtxoFilteredTransactionBuilder {
        return copy(timeWindow = true)
    }

    override fun withCommands(): UtxoFilteredTransactionBuilder {
        return withCommands { true }
    }

    override fun withCommands(predicate: Predicate<Command>): UtxoFilteredTransactionBuilder {
        return copy(
            commands =
            FilterParams(
                FilterType.AUDIT,
                predicate,
                Command::class.java
            )
        )
    }

    override fun withCommandsSize(): UtxoFilteredTransactionBuilder {
        return copy(commands = FilterParams(FilterType.SIZE))
    }

    override fun withInputStates(): UtxoFilteredTransactionBuilder {
        return withInputStates { true }
    }

    override fun withInputStates(predicate: Predicate<StateRef>): UtxoFilteredTransactionBuilder {
        return copy(
            inputStates =
            FilterParams(
                FilterType.AUDIT,
                predicate,
                StateRef::class.java
            )
        )
    }

    override fun withInputStatesSize(): UtxoFilteredTransactionBuilder {
        return copy(inputStates = FilterParams((FilterType.SIZE)))
    }

    override fun withOutputStates(): UtxoFilteredTransactionBuilder {
        return withOutputStates { true }
    }

    override fun withOutputStates(predicate: Predicate<ContractState>): UtxoFilteredTransactionBuilder {
        return copy(
            outputStates =
            FilterParams(
                FilterType.AUDIT,
                predicate,
                ContractState::class.java
            )
        )
    }

    override fun withOutputStatesSize(): UtxoFilteredTransactionBuilder {
        return copy(outputStates = FilterParams((FilterType.SIZE)))
    }

    override fun withReferenceStates(): UtxoFilteredTransactionBuilder {
        return withReferenceStates { true }
    }

    override fun withReferenceStates(predicate: Predicate<StateRef>): UtxoFilteredTransactionBuilder {
        return copy(
            referenceStates =
            FilterParams(
                FilterType.AUDIT,
                predicate,
                StateRef::class.java
            )
        )
    }

    override fun withReferenceStatesSize(): UtxoFilteredTransactionBuilder {
        return copy(referenceStates = FilterParams((FilterType.SIZE)))
    }

    override fun withSignatories(): UtxoFilteredTransactionBuilder {
        return withInputStates { true }
    }

    override fun withSignatories(predicate: Predicate<PublicKey>): UtxoFilteredTransactionBuilder {
        return copy(
            signatories =
            FilterParams(
                FilterType.AUDIT,
                predicate,
                PublicKey::class.java
            )
        )
    }

    override fun withSignatoriesSize(): UtxoFilteredTransactionBuilder {
        return copy(signatories = FilterParams((FilterType.SIZE)))
    }

    override fun build(): UtxoFilteredTransaction {
        return UtxoFilteredTransactionBase(
            signedTransaction,
            this,
            mapOf(
                UtxoComponentGroup.COMMANDS to commands,
                UtxoComponentGroup.INPUTS to inputStates,
                UtxoComponentGroup.REFERENCES to referenceStates,
                UtxoComponentGroup.SIGNATORIES to signatories,
                UtxoComponentGroup.OUTPUTS to outputStates
            )
        )
    }
}

data class FilterParams (
    val filterType: FilterType,
    val predicate: Predicate<*>? = null,
    val classType: Class<*>? = null
)

enum class FilterType {
    AUDIT, SIZE
}