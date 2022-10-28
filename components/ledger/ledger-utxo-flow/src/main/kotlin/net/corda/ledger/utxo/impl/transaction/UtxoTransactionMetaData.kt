package net.corda.ledger.utxo.impl.transaction

class UtxoTransactionMetaData {
    enum class TransactionSubtype {
        NOTARY_CHANGE,
        GENERAL
    }
    companion object {
        const val LEDGER_VERSION = "0.001"
    }
}

