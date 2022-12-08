package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder

/**
 * Internal interface of [UtxoFilteredTransactionBuilder] to access the filter parameters.
 */
interface UtxoFilteredTransactionBuilderInternal : UtxoFilteredTransactionBuilder {

    val notary: ComponentGroupFilterParameters?

    val signatories: ComponentGroupFilterParameters?

    val inputStates: ComponentGroupFilterParameters?

    val referenceInputStates: ComponentGroupFilterParameters?

    val outputStates: ComponentGroupFilterParameters?

    val commands: ComponentGroupFilterParameters?
}