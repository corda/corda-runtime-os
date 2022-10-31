package net.corda.ledger.common.flow.transaction.filtered

import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * [FilteredTransaction] is a [WireTransaction] that has had its [WireTransaction.componentGroupLists] filtered to obfuscate some data
 * contained within it.
 */
interface FilteredTransaction {

    /**
     * Gets the transaction id.
     */
    val id: SecureHash

    /**
     * Gets the [MerkleProof] calculated from the [filteredComponentGroups].
     */
    val topLevelMerkleProof: MerkleProof

    /**
     * Gets the [FilteredComponentGroup]s of the transaction.
     */
    val filteredComponentGroups: Map<Int, FilteredComponentGroup>

    /**
     * Gets the [TransactionMetadata] of the transaction.
     * 
     * @throws IllegalStateException If the [TransactionMetadata] component group does not exist at index 0.
     */
    val metadata: TransactionMetadata

    /**
     * Verifies the structure of the transaction and the [MerkleProof]s contained within it
     * 
     * @throws FilteredTransactionVerificationException If the transaction fails to verify.
     */
    fun verify()

    /**
     * Gets the component group at [componentGroupIndex] where each component is in its serialized form.
     *
     * The content returned for a component group that had a size-only proof applied to it will not match the original content, therefore;
     * this method should not be used on component groups where these proofs were applied.
     *
     * @return The component group at [componentGroupIndex], or null if it did not exist in the original transaction or was filtered out.
     */
    fun getComponentGroupContent(componentGroupIndex: Int): List<ByteArray>?
}