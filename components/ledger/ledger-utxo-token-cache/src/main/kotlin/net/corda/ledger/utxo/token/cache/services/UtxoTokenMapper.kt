package net.corda.ledger.utxo.token.cache.services

import javax.persistence.Tuple

/**
 * Used by [UtxoRepositoryImpl.findTransactionSignatures] to map DB rows to transaction's components group lists
 */
class UtxoTokenMapper() : TokenMapper {
    override fun map(tuples: List<Tuple>): Map<Int, List<ByteArray>> {
        val componentGroupLists: MutableMap<Int, MutableList<ByteArray>> = mutableMapOf()
        tuples.forEach { columns ->
            println(columns[0])
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
        return componentGroupLists
    }
}