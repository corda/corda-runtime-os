package net.corda.ledger.persistence.consensual.impl

import net.corda.ledger.common.data.transaction.TransactionStatus

interface ConsensualQueryProvider {
    companion object {
        @JvmField
        val UNVERIFIED = TransactionStatus.UNVERIFIED.value
    }

    val persistTransaction: String

    val persistTransactionComponentLeaf: String

    val persistTransactionStatus: String

    val persistTransactionSignature: String

    val persistTransactionCpk: String
}
