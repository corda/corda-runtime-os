package com.r3.corda.notary.plugin.nonvalidating.api

import com.r3.corda.notary.plugin.common.BaseNotarisationPayload
import com.r3.corda.notary.plugin.common.NotarisationRequestSignature
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * Container for the transaction and notarisation request signature.
 * This is the payload that gets sent by a client to a notary service for committing the input states of the [transaction].
 */
@CordaSerializable
class NonValidatingNotarisationPayload(
    transaction: Any,
    // TODO CORE-4667 needs to be communicated in a more elegant way as part of Ledger / Transcend
    val numOutputs: Int,
    requestSignature: NotarisationRequestSignature
): BaseNotarisationPayload(
    transaction,
    requestSignature,
    listOf(UtxoSignedTransaction::class.java)
)
