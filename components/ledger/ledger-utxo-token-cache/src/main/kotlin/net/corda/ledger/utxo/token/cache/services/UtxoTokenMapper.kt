package net.corda.ledger.utxo.token.cache.services

import java.math.BigDecimal
import javax.persistence.Tuple
import net.corda.crypto.core.parseSecureHash
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.v5.ledger.utxo.StateRef

/**
 * Used by [UtxoRepositoryImpl.findTransactionSignatures] to map DB rows to transaction's components group lists
 */
class UtxoTokenMapper() : TokenMapper {
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
                it.getString(Column.TOKEN_TAG),
                it.getString(Column.TOKEN_OWNER_HASH)
            )
        }
    }

    private fun Tuple.getString(column: Int): String {
        return this[column].toString()
    }

    private fun Tuple.getInt(column: Int): Int {
        return (this[column] as Number).toInt()
    }

    private fun Tuple.getBigDecimal(column: Int): BigDecimal {
        return this[column] as BigDecimal
    }

    private data class DbCachedToken(
        override val stateRef: String,
        override val amount: BigDecimal,
        override val tag: String,
        override val ownerHash: String
    ) : CachedToken {
        override fun toAvro(): Token {
            TODO("Not yet implemented")
        }
    }
}