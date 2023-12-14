package com.r3.corda.notary.plugin.common

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

/**
 * A representation of a transaction (non-validating). It is easier to perform operations on this representation than
 * on the actual transaction object (e.g. FilteredTransaction).
 */
data class NotaryTransactionDetails(
    private val id: SecureHash,
    private val metadata: TransactionMetadata,
    val numOutputs: Int,
    val timeWindow: TimeWindow,
    val inputs: List<StateRef>,
    val references: List<StateRef>,
    // TODO CORE-8976 This is not used for now but will be needed when the notary check is added
    val notaryName: MemberX500Name,
    val notaryKey: PublicKey
) : TransactionWithMetadata {

    override fun getId(): SecureHash {
        return id
    }

    override fun getMetadata(): TransactionMetadata {
        return metadata
    }
}