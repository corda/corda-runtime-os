package com.r3.corda.notary.plugin.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef

/**
 * A notarisation request specifies a list of states to consume and the id of the consuming transaction. Its primary
 * purpose is for notarisation traceability – a signature over the notarisation request, [NotarisationRequestSignature],
 * allows a notary to prove that a certain party requested the consumption of a particular state.
 */
@CordaSerializable
data class NotarisationRequest(
    val statesToConsume: List<StateRef>,
    val transactionId: SecureHash
)