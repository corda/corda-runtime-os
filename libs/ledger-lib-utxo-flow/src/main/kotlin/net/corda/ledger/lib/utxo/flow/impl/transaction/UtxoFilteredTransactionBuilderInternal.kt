package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.filtered.ComponentGroupFilterParameters
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder

/**
 * Internal interface of [UtxoFilteredTransactionBuilder] to access the filter parameters.
 */
interface UtxoFilteredTransactionBuilderInternal : UtxoFilteredTransactionBuilder {

    val notary: Boolean

    val timeWindow: Boolean

    val signatories: ComponentGroupFilterParameters?

    val inputStates: ComponentGroupFilterParameters?

    val referenceStates: ComponentGroupFilterParameters?

    val outputStates: ComponentGroupFilterParameters?

    val commands: ComponentGroupFilterParameters?
}
