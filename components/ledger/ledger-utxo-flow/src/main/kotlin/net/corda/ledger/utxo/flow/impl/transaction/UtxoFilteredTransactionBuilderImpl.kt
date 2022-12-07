package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoFilteredTransactionFactory
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransactionBuilder
import java.security.PublicKey
import java.util.function.Predicate

data class UtxoFilteredTransactionBuilderImpl(
    private val utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory,
    private val signedTransaction: UtxoSignedTransactionInternal,
    override val notaryPredicate: Predicate<Party>? = null,
    override val signatoriesPredicate: Predicate<PublicKey>? = null,
    override val inputStatesPredicate: Predicate<StateRef>? = null,
    override val referenceInputStatesPredicate: Predicate<StateRef>? = null,
    override val outputStatesPredicate: Predicate<StateAndRef<*>>? = null,
    override val commandsPredicate: Predicate<Command>? = null
) : UtxoFilteredTransactionBuilder, UtxoFilteredTransactionBuilderInternal {

    @Suspendable
    override fun withNotary(): UtxoFilteredTransactionBuilder {
        return copy(notaryPredicate =  { true })
    }

    @Suspendable
    override fun withNotary(predicate: Predicate<Party>): UtxoFilteredTransactionBuilder {
        return copy(notaryPredicate =  predicate)
    }

    @Suspendable
    override fun withSignatories(): UtxoFilteredTransactionBuilder {
        return copy(signatoriesPredicate =  { true })
    }

    @Suspendable
    override fun withSignatories(predicate: Predicate<PublicKey>): UtxoFilteredTransactionBuilder {
        return copy(signatoriesPredicate =  predicate)
    }

    @Suspendable
    override fun withInputStates(): UtxoFilteredTransactionBuilder {
        return copy(inputStatesPredicate =  { true })
    }

    @Suspendable
    override fun withInputStates(predicate: Predicate<StateRef>): UtxoFilteredTransactionBuilder {
        return copy(inputStatesPredicate =  predicate)
    }

    @Suspendable
    override fun withReferenceInputStates(): UtxoFilteredTransactionBuilder {
        return copy(referenceInputStatesPredicate =  { true })
    }

    @Suspendable
    override fun withReferenceInputStates(predicate: Predicate<StateRef>): UtxoFilteredTransactionBuilder {
        return copy(referenceInputStatesPredicate =  predicate)
    }

    @Suspendable
    override fun withOutputStates(): UtxoFilteredTransactionBuilder {
        return copy(outputStatesPredicate =  { true })
    }

    @Suspendable
    override fun withOutputStates(predicate: Predicate<StateAndRef<*>>): UtxoFilteredTransactionBuilder {
        return copy(outputStatesPredicate =  predicate)
    }

    @Suspendable
    override fun withCommands(): UtxoFilteredTransactionBuilder {
        return copy(commandsPredicate =  { true })
    }

    @Suspendable
    override fun withCommands(predicate: Predicate<Command>): UtxoFilteredTransactionBuilder {
        return copy(commandsPredicate =  predicate)
    }

    @Suspendable
    override fun toFilteredTransaction(): UtxoFilteredTransaction {
        return utxoFilteredTransactionFactory.create(signedTransaction, this)
    }
}