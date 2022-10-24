package com.r3.corda.notary.plugin.common

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow

/**
 * The minimum amount of information needed to notarise a transaction. Note that this does not include
 * any sensitive transaction details.
 */
data class TransactionParts(
    val id: SecureHash,
    val inputs: List<StateRef>,
    val numOutputs: Int,
    val timeWindow: TimeWindow,
    val references: List<StateRef> = emptyList()
)