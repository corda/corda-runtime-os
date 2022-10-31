package com.r3.corda.notary.plugin.nonvalidating

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow

/**
 * The minimum amount of information needed to notarise a transaction. Note that this does not include
 * any sensitive transaction details.
 */
data class NonValidatingNotaryTransactionDetails(
    val id: SecureHash,
    val numOutputs: Int,
    val timeWindow: TimeWindow,
    val inputs: List<StateRef>,
    val references: List<StateRef>
)
