package net.corda.utxo.token.sync.converters.impl

import net.corda.utxo.token.sync.converters.DbRecordConverter
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.LEAF_INDEX
import net.corda.utxo.token.sync.converters.UtxoTransactionOutputDbFields.TRANSACTION_ID
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import javax.persistence.Tuple

class DbRecordConverterImpl : DbRecordConverter {

    override fun convertTokenKey(record: Tuple): TokenPoolKeyRecord {
        return TokenPoolKeyRecord(
            tokenType = record.get(UtxoTransactionOutputDbFields.TOKEN_TYPE) as String,
            issuerHash = record.get(UtxoTransactionOutputDbFields.TOKEN_ISSUER_HASH) as String,
            notaryX500Name = record.get(UtxoTransactionOutputDbFields.TOKEN_NOTARY_X500_NAME) as String,
            symbol = record.get(UtxoTransactionOutputDbFields.TOKEN_SYMBOL) as String
        )
    }

    override fun convertTokenRecord(record: Tuple): TokenRecord {
        return TokenRecord(
            key = convertTokenKey(record),
            stateRef = getStateRefString(record),
            amount = record.get(UtxoTransactionOutputDbFields.TOKEN_AMOUNT) as BigDecimal,
            ownerHash = record.get(UtxoTransactionOutputDbFields.TOKEN_OWNER_HASH) as String?,
            tag = record.get(UtxoTransactionOutputDbFields.TOKEN_TAG) as String?,
            lastModified = record.getInstant(UtxoTransactionOutputDbFields.CREATED)
        )
    }

    override fun convertToTokenRef(record: Tuple): TokenRefRecord {
        return TokenRefRecord(
            stateRef = getStateRefString(record),
            lastModified = record.getInstant(UtxoTransactionOutputDbFields.CREATED)
        )
    }

    private fun getStateRefString(record: Tuple): String {
        return "${record.get(TRANSACTION_ID)}:${record.get(LEAF_INDEX)}"
    }

    private fun Tuple.getInstant(fieldName:String):Instant{
        return (get(fieldName) as Timestamp).toInstant()
    }
}
