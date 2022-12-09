package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactory
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import java.security.PublicKey
import java.util.function.Predicate

@Suppress("TooManyFunctions")
data class UtxoFilteredTransactionBuilderImpl(
    private val utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory,
    private val signedTransaction: UtxoSignedTransactionInternal,
    override val notary: ComponentGroupFilterParameters? = null,
    override val signatories: ComponentGroupFilterParameters? = null,
    override val inputStates: ComponentGroupFilterParameters? = null,
    override val referenceInputStates: ComponentGroupFilterParameters? = null,
    override val outputStates: ComponentGroupFilterParameters? = null,
    override val commands: ComponentGroupFilterParameters? = null
) : UtxoFilteredTransactionBuilder, UtxoFilteredTransactionBuilderInternal {

    @Suspendable
    override fun withNotaryAndTimeWindow(): UtxoFilteredTransactionBuilderInternal {
        return copy(
            notary = ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.NOTARY.ordinal,
                Any::class.java,
            ) { true }
        )
    }

    @Suspendable
    override fun withSignatoriesSize(): UtxoFilteredTransactionBuilderInternal {
        return copy(signatories = ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.SIGNATORIES.ordinal))
    }

    @Suspendable
    override fun withSignatories(): UtxoFilteredTransactionBuilderInternal {
        return withSignatories { true }
    }

    @Suspendable
    override fun withSignatories(predicate: Predicate<PublicKey>): UtxoFilteredTransactionBuilderInternal {
        return copy(
            signatories = ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.SIGNATORIES.ordinal,
                PublicKey::class.java,
                predicate
            )
        )
    }

    @Suspendable
    override fun withInputStatesSize(): UtxoFilteredTransactionBuilderInternal {
        return copy(inputStates = ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.INPUTS.ordinal))
    }

    @Suspendable
    override fun withInputStates(): UtxoFilteredTransactionBuilderInternal {
        return withInputStates { true }
    }

    @Suspendable
    override fun withInputStates(predicate: Predicate<StateRef>): UtxoFilteredTransactionBuilderInternal {
        return copy(
            inputStates = ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.INPUTS.ordinal,
                StateRef::class.java,
                predicate
            )
        )
    }

    @Suspendable
    override fun withReferenceInputStatesSize(): UtxoFilteredTransactionBuilderInternal {
        return copy(referenceInputStates = ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.REFERENCES.ordinal))
    }

    @Suspendable
    override fun withReferenceInputStates(): UtxoFilteredTransactionBuilderInternal {
        return withReferenceInputStates { true }
    }

    @Suspendable
    override fun withReferenceInputStates(predicate: Predicate<StateRef>): UtxoFilteredTransactionBuilderInternal {
        return copy(
            referenceInputStates = ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.REFERENCES.ordinal,
                StateRef::class.java,
                predicate
            )
        )
    }

    @Suspendable
    override fun withOutputStatesSize(): UtxoFilteredTransactionBuilderInternal {
        return copy(outputStates = ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.OUTPUTS.ordinal))
    }

    @Suspendable
    override fun withOutputStates(): UtxoFilteredTransactionBuilderInternal {
        return withOutputStates { true }
    }

    @Suspendable
    override fun withOutputStates(predicate: Predicate<ContractState>): UtxoFilteredTransactionBuilderInternal {
        return copy(
            outputStates = ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.OUTPUTS.ordinal,
                ContractState::class.java,
                predicate
            )
        )
    }

    @Suspendable
    override fun withCommandsSize(): UtxoFilteredTransactionBuilderInternal {
        return copy(commands = ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal))
    }

    @Suspendable
    override fun withCommands(): UtxoFilteredTransactionBuilderInternal {
        return withCommands { true }
    }

    @Suspendable
    override fun withCommands(predicate: Predicate<Command>): UtxoFilteredTransactionBuilderInternal {
        return copy(
            commands = ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.COMMANDS.ordinal,
                Command::class.java,
                predicate
            )
        )
    }

    @Suspendable
    override fun toFilteredTransaction(): UtxoFilteredTransaction {
        return utxoFilteredTransactionFactory.create(signedTransaction, this)
    }
}