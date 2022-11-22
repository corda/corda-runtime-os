package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer

interface UtxoPersistenceService {

    fun findTransaction(id: String): SignedTransactionContainer?

    fun persistTransaction(transaction: UtxoTransactionReader)
}
