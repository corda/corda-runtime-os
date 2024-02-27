package net.corda.ledger.utxo.data.transaction

class UtxoTransactionMetadata {
    enum class TransactionSubtype {
        NOTARY_CHANGE,
        GENERAL
    }
    companion object {
        const val LEDGER_VERSION = 1
    }
}
