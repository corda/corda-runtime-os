package com.r3.corda.notary.plugin.nonvalidating.api

import com.r3.corda.notary.plugin.common.BaseNotarizationPayload
import com.r3.corda.notary.plugin.common.NotarizationRequestSignature
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import java.security.PublicKey

/**
 * Container for the transaction and notarization request signature.
 * This is the payload that gets sent by a client to a notary service for committing the input states of the [transaction].
 */
@CordaSerializable
class NonValidatingNotarizationPayload(
    transaction: UtxoFilteredTransaction,
    requestSignature: NotarizationRequestSignature,
    notaryKey: PublicKey
): BaseNotarizationPayload(
    transaction,
    requestSignature,
    notaryKey,
    listOf(UtxoFilteredTransaction::class.java)
)
