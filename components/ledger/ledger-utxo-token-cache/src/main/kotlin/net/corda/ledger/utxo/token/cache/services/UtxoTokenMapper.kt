package net.corda.ledger.utxo.token.cache.services

import net.corda.crypto.core.parseSecureHash
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.DbCachedToken
import net.corda.v5.ledger.utxo.StateRef
import java.math.BigDecimal
import javax.persistence.Tuple

class UtxoTokenMapper : TokenMapper {
    private object Column {
        const val TRANSACTION_ID = 0
        const val LEAF_IDX = 1
        const val TOKEN_TAG = 2
        const val TOKEN_OWNER_HASH = 3
        const val TOKEN_AMOUNT = 4
    }

    override fun map(tuples: List<Tuple>): Collection<CachedToken> {
        return tuples.map {
            DbCachedToken(
                StateRef(
                    parseSecureHash(it.getString(Column.TRANSACTION_ID)),
                    it.getInt(Column.LEAF_IDX)
                ).toString(),
                it.getBigDecimal(Column.TOKEN_AMOUNT),
                it.getNullableString(Column.TOKEN_TAG),
                it.getNullableString(Column.TOKEN_OWNER_HASH)
            )
        }
    }

    private fun Tuple.getString(column: Int): String {
        return this[column].toString()
    }

    private fun Tuple.getNullableString(column: Int): String? {
        return this[column]?.toString()
    }

    private fun Tuple.getInt(column: Int): Int {
        return (this[column] as Number).toInt()
    }

    private fun Tuple.getBigDecimal(column: Int): BigDecimal {
        return this[column] as BigDecimal
    }
}
