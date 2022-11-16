package net.corda.ledger.persistence.utxo

interface UtxoPersistenceService {
    fun persistTransaction(transaction: UtxoTransactionReader)
}
