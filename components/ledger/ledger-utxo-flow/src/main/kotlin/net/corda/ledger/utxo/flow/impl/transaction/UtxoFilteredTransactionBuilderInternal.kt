package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransactionBuilder
import java.security.PublicKey
import java.util.function.Predicate

interface UtxoFilteredTransactionBuilderInternal : UtxoFilteredTransactionBuilder {

    val notaryPredicate: Predicate<Party>?

    val signatoriesPredicate: Predicate<PublicKey>?

    val inputStatesPredicate: Predicate<StateRef>?

    val referenceInputStatesPredicate: Predicate<StateRef>?

    val outputStatesPredicate: Predicate<StateAndRef<*>>?

    val commandsPredicate: Predicate<Command>?
}