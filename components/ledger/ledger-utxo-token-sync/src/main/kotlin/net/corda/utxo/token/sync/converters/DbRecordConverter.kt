package net.corda.utxo.token.sync.converters

import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import javax.persistence.Tuple

interface DbRecordConverter {
    fun convertToTokenRef(record: Tuple): TokenRefRecord

    fun convertTokenKey(record: Tuple): TokenPoolKeyRecord

    fun convertTokenRecord(record: Tuple): TokenRecord
}

