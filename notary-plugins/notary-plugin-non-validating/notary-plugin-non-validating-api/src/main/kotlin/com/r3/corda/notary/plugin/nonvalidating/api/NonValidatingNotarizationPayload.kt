package com.r3.corda.notary.plugin.nonvalidating.api

import com.r3.corda.notary.plugin.common.BaseNotarizationPayload
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import java.security.PublicKey

/**
 * Contains the data exchanged between client and server plugins for the non-validating notary
 * protocol.
 */
@CordaSerializable
class NonValidatingNotarizationPayload(
    transaction: UtxoFilteredTransaction,
    notaryKey: PublicKey,
    notarizationType: NotarizationType
): BaseNotarizationPayload(
    transaction,
    notaryKey,
    notarizationType,
    listOf(UtxoFilteredTransaction::class.java)
)
