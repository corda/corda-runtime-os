package net.corda.ledger.persistence.consensual.impl

import net.corda.ledger.persistence.common.ComponentGroupMapper
import javax.persistence.Tuple

/**
 * Used by [ConsensualRepositoryImpl.findTransaction] to map DB rows to transaction's components group lists
 */
class ConsensualComponentGroupMapper : ComponentGroupMapper {
    override fun map(tuples: List<Tuple>): Map<Int, List<ByteArray>> {
        val componentGroupLists: MutableMap<Int, MutableList<ByteArray>> = mutableMapOf()
        tuples.forEach { columns ->
            val groupIdx = (columns[4] as Number).toInt()
            val leafIdx = (columns[5] as Number).toInt()
            val data = columns[6] as ByteArray
            val componentList = componentGroupLists[groupIdx]
            if (componentList == null) {
                componentGroupLists[groupIdx] = mutableListOf(data)
            } else {
                check(componentList.size == leafIdx) {
                    // Missing leaf indices indicate that data is corrupted
                    val id = columns[0] as String
                    "Missing data for consensual transaction with ID: $id, groupIdx: $groupIdx, leafIdx: ${componentList.size}"
                }
                componentList.add(data)
            }
        }
        return componentGroupLists
    }
}