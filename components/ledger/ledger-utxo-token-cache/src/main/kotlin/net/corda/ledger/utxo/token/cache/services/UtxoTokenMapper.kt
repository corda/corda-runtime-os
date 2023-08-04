package net.corda.ledger.utxo.token.cache.services

import javax.persistence.Tuple
import net.corda.ledger.utxo.token.cache.entities.AvailTokenBucket
import net.corda.ledger.utxo.token.cache.entities.CachedToken

/**
 * Used by [UtxoRepositoryImpl.findTransactionSignatures] to map DB rows to transaction's components group lists
 */
class UtxoTokenMapper() : TokenMapper {
    private enum class Column {
        TRANSACTION_ID,
        GROUP_IDX,
        LEAF_IDX,
        TYPE,
        TOKEN_TYPE,
        TOKEN_ISSUER_HASH,
        TOKEN_NOTARY_X500_NAME,
        TOKEN_SYMBOL,
        TOKEN_TAG,
        TOKEN_OWNER_HASH,
        TOKEN_AMOUNT,
        CREATED
    }
    override fun map(tuples: List<Tuple>): AvailTokenBucket {
        val tokens: MutableList<CachedToken> = ArrayList()
        tuples.forEach { columns ->
            val transactionId = columns[0]
            val leafIdx = columns[1] as Int
//            val stateRef = StateRef(parseSecureHash(transactionId), leafIdx)
//
//            Token(stateRef.toString(), )
            println()
            println(columns[1])
            println(columns[2])
            println(columns[3])
            println(columns[4])
            println(columns[5])
            println(columns[6])
            println(columns[7])
            println(columns[8])
            println(columns[9])
            println(columns[10])
//            val groupIdx = (columns[0] as Number).toInt()
      //      val leafIdx = (columns[1] as Number).toInt()
//            val data = columns[2] as ByteArray
        }
        return AvailTokenBucket(tokens)
    }
}