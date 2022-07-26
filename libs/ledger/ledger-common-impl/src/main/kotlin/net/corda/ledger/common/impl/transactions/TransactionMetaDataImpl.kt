package net.corda.ledger.common.impl.transactions

import net.corda.v5.ledger.common.transactions.TRANSACTION_META_DATA_CPK_IDENTIFIERS_KEY
import net.corda.v5.ledger.common.transactions.TRANSACTION_META_DATA_LEDGER_MODEL_KEY
import net.corda.v5.ledger.common.transactions.TRANSACTION_META_DATA_LEDGER_VERSION_KEY
import net.corda.v5.ledger.common.transactions.TransactionMetaData

//TODO(guarantee its serialization is deterministic)
class TransactionMetaDataImpl: TransactionMetaData, HashMap<String, Any>() {
    fun getLedgerModel(){
        this[TRANSACTION_META_DATA_LEDGER_MODEL_KEY]
    }
    fun getLedgerVersion(){
        this[TRANSACTION_META_DATA_LEDGER_VERSION_KEY]
    }
    fun cpkIdentifiers(){
        this[TRANSACTION_META_DATA_CPK_IDENTIFIERS_KEY]
    }
}