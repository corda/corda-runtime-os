package com.r3.corda.notary.plugin.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef

/**
 * A notarization request specifies a list of states to consume and the id of the consuming transaction. Its primary
 * purpose is for notarization traceability – a signature over the notarization request, [NotarizationRequestSignature],
 * allows a notary to prove that a certain party requested the consumption of a particular state.
 */
@CordaSerializable
data class NotarizationRequest(
    val statesToConsume: List<StateRef>,
    val transactionId: SecureHash
)
