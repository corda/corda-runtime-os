package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.persistence.common.ComponentGroupMapper
import javax.persistence.Tuple

/**
 * Used by [UtxoRepositoryImpl.findTransactionSignatures] to map DB rows to transaction's components group lists
 */
class UtxoComponentGroupMapper(private val transactionId: String) : ComponentGroupMapper {
    override fun map(tuples: List<Tuple>): Map<Int, List<ByteArray>> {
        val componentGroupLists: MutableMap<Int, MutableList<ByteArray>> = mutableMapOf()
        tuples.forEach { columns ->
            val groupIdx = (columns[0] as Number).toInt()
            val leafIdx = (columns[1] as Number).toInt()
            val data = columns[2] as ByteArray
            val componentList = componentGroupLists[groupIdx]
            if (componentList == null) {
                componentGroupLists[groupIdx] = mutableListOf(data)
            } else {
                check(componentList.size == leafIdx) {
                    // Missing leaf indices indicate that data is corrupted
                    "Missing data for UTXO transaction with ID: $transactionId, groupIdx: $groupIdx, leafIdx: ${componentList.size}"
                }
                componentList.add(data)
            }
        }
        return componentGroupLists
    }
}