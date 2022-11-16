package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.persistence.common.TuplesMapper
import javax.persistence.Tuple

/**
 * Used by [UtxoRepositoryImpl.findTransactionSignatures] to map DB rows to transaction's components group lists
 */
class ComponentGroupListsTuplesMapper(val transactionId: String): TuplesMapper<List<ByteArray>> {
    override fun map(tuples: List<Tuple>): List<List<ByteArray>> {
        val componentGroupLists: MutableList<MutableList<ByteArray>> = mutableListOf()
        var componentsList: MutableList<ByteArray> = mutableListOf()
        var expectedGroupIdx = 0
        tuples.forEach { columns ->
            val groupIdx = (columns[0] as Number).toInt()
            val leafIdx = (columns[1] as Number).toInt()
            val data = columns[2] as ByteArray
            while (groupIdx > expectedGroupIdx) {
                // Add empty lists for missing group indices
                componentGroupLists.add(componentsList)
                componentsList = mutableListOf()
                expectedGroupIdx++
            }
            check(componentsList.size == leafIdx) {
                // Missing leaf indices indicate that data is corrupted
                "Missing data for UTXO transaction with ID: $transactionId, groupIdx: $groupIdx, leafIdx: ${componentsList.size}"
            }
            componentsList.add(data)
        }
        componentGroupLists.add(componentsList)
        return componentGroupLists
    }
}