package com.r3.corda.notary.plugin.nonvalidating.api

import com.r3.corda.notary.plugin.common.NotarisationRequestSignature
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.v5.base.annotations.CordaSerializable

/**
 * Container for the transaction and notarisation request signature.
 * This is the payload that gets sent by a client to a notary service for committing the input states of the [transaction].
 */
@CordaSerializable
class NonValidatingNotarisationPayload(
    val transaction: FilteredTransaction,
    val requestSignature: NotarisationRequestSignature
)
