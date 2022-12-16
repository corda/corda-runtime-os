package com.r3.corda.notary.plugin.nonvalidating.server

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow

/**
 * A representation of a transaction (non-validating). It is easier to perform operations on this representation than
 * on the actual transaction object (e.g. FilteredTransaction).
 */
data class NonValidatingNotaryTransactionDetails(
    val id: SecureHash,
    val numOutputs: Int,
    val timeWindow: TimeWindow,
    val inputs: Collection<StateRef>,
    val references: Collection<StateRef>,
    // TODO CORE-8976 This is not used for now but will be needed when the notary check is added
    val notary: Party
)