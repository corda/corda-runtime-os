package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.crypto.SecureHash
import java.util.Collections

/**
 * Provides a way to topologically sort SignedTransactions represented just their [SecureHash] IDs. This means that given any two
 * transactions T1 and T2 in the list returned by [complete] if T1 is a dependency of T2 then T1 will occur earlier than T2.
 */
class TopologicalSort {
    private val forwardGraph = HashMap<SecureHash, MutableSet<SecureHash>>()
    val transactionIds = LinkedHashSet<SecureHash>()
    private val nonDupeHash = HashMap<SecureHash, SecureHash>()
    private fun dedupe(sh: SecureHash): SecureHash = nonDupeHash.getOrPut(sh) { sh }

    /**
     * Add a transaction to the to-be-sorted set of transactions.
     * @param transactionId The ID of the transaction.
     * @param dependentIds the IDs of all the transactions [transactionId] depends on.
     */
    fun add(transactionId: SecureHash, dependentIds: Set<SecureHash>) {
        val dedupedTransactionId = dedupe(transactionId)
        require(transactionIds.add(dedupedTransactionId)) { "Transaction ID $dedupedTransactionId already seen" }
        dependentIds.forEach {
            // Note that we use a LinkedHashSet here to make the traversal deterministic (as long as the input list is).
            val deDupeIt = dedupe(it)
            forwardGraph.computeIfAbsent(deDupeIt) { LinkedHashSet() }.add(dedupedTransactionId)
        }
    }

    /**
     * Return the sorted list of transaction IDs.
     */
    fun complete(): ArrayList<SecureHash> {
        val visited = HashSet<SecureHash>(transactionIds.size)
        val result = ArrayList<SecureHash>(transactionIds.size)

        fun visit(transactionId: SecureHash) {
            if (visited.add(transactionId)) {
                forwardGraph[transactionId]?.forEach(::visit)
                result += transactionId
            }
        }

        transactionIds.forEach(::visit)
        return result.apply(Collections::reverse)
    }

    // probably remove
    fun isNotEmpty(): Boolean {
        return transactionIds.isNotEmpty()
    }

    val size: Int get() = transactionIds.size
}