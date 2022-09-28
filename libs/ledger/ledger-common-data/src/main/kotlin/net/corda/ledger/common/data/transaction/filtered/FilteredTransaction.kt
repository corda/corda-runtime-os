package net.corda.ledger.common.data.transaction.filtered

import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof

interface FilteredTransaction {

    val id: SecureHash

    val componentGroupMerkleProof: MerkleProof

    val filteredComponentGroups: Map<Int, FilteredComponentGroup>

    val metadata: TransactionMetadata

    fun verify()

    fun getComponentGroupContent(componentGroupOrdinal: Int): List<ByteArray>?
}