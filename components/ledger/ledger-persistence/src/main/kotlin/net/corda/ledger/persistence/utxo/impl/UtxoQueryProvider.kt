package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.common.data.transaction.TransactionStatus

interface UtxoQueryProvider {
    companion object {
        @JvmField
        val UNVERIFIED = TransactionStatus.UNVERIFIED.value
    }

    val persistTransaction: String

    val persistTransactionComponentLeaf: String

    val persistTransactionCpk: String

    val persistTransactionOutput: String

    fun persistTransactionVisibleStates(consumed: Boolean): String

    val persistTransactionSignature: String

    val persistTransactionSource: String

    val persistTransactionStatus: String

    val persistSignedGroupParameters: String
}
