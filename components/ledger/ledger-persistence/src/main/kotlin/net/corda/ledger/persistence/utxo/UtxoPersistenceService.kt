package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.ledger.common.transaction.CordaPackageSummary

interface UtxoPersistenceService {
    fun persistTransaction(transaction: UtxoTransactionReader)

    fun persistTransactionIfDoesNotExist(
        transaction: SignedTransactionContainer,
        transactionStatus: TransactionStatus,
        account: String
    ): Pair<String?, List<CordaPackageSummary>>

    fun updateStatus(id: String, transactionStatus: TransactionStatus)

    fun findTransaction(id: String, transactionStatus: TransactionStatus): SignedTransactionContainer?
}
