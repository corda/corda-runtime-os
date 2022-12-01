package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.v5.ledger.common.transaction.CordaPackageSummary

interface UtxoPersistenceService {
    fun persistTransaction(transaction: UtxoTransactionReader)

    fun persistTransactionIfDoesNotExist(
        transaction: SignedTransactionContainer,
        transactionStatus: String,
        account: String
    ): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: String)

    fun findTransaction(id: String, transactionStatus: String): SignedTransactionContainer?
}
