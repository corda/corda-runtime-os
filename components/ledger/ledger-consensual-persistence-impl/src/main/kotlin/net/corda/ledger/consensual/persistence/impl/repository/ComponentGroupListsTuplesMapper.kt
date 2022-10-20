package net.corda.ledger.consensual.persistence.impl.repository

import javax.persistence.Tuple

/**
 * Used by [ConsensualLedgerRepository.findTransaction] to map DB rows to transaction's components group lists
 */
class ComponentGroupListsTuplesMapper: TuplesMapper<List<ByteArray>> {
    override fun map(tuples: List<Tuple>): List<List<ByteArray>> {
        val componentGroupLists: MutableList<MutableList<ByteArray>> = mutableListOf()
        var componentsList: MutableList<ByteArray> = mutableListOf()
        var expectedGroupIdx = 0
        tuples.forEach { columns ->
            val groupIdx = (columns[4] as Number).toInt()
            val leafIdx = (columns[5] as Number).toInt()
            val data = columns[6] as ByteArray
            while (groupIdx > expectedGroupIdx) {
                // Add empty lists for missing group indices
                componentGroupLists.add(componentsList)
                componentsList = mutableListOf()
                expectedGroupIdx++
            }
            check(componentsList.size == leafIdx) {
                // Missing leaf indices indicate that data is corrupted
                val id = columns[0] as String
                "Missing data for transaction with ID: $id, groupIdx: $groupIdx, leafIdx: ${componentsList.size}"
            }
            componentsList.add(data)
        }
        componentGroupLists.add(componentsList)
        return componentGroupLists
    }
}