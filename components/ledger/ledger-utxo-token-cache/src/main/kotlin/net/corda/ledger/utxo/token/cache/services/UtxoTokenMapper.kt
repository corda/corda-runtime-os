package net.corda.ledger.utxo.token.cache.services

import java.math.BigDecimal
import java.nio.ByteBuffer
import javax.persistence.Tuple
import net.corda.crypto.core.parseSecureHash
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.v5.ledger.utxo.StateRef

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
            return createToken(stateRef, amount, tag, ownerHash)
        }

        private fun createToken(stateRef: String, amount: BigDecimal, tag: String, ownerHash: String): Token =
            Token().apply {
                this.stateRef = stateRef
                this.amount = TokenAmount(
                    amount.scale(),
                    ByteBuffer.wrap(amount.unscaledValue().toByteArray())
                )
                this.tag = tag
                this.ownerHash = ownerHash
            }
    }
}