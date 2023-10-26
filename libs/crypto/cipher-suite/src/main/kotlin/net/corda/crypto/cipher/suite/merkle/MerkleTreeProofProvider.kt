package net.corda.crypto.cipher.suite.merkle

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * [MerkleTreeProofProvider] creates [MerkleProof]s.
 */
@DoNotImplement
interface MerkleTreeProofProvider {
    /**
     * Creates a [MerkleProof]
     *
     * @param proofType
     * @param treeSize
     * @param leaves The leaves of the tree.
     * @param hashes
     *
     * @return A new [MerkleProof] instance.
     */
    @Suspendable
    fun createMerkleProof(
      proofType: MerkleProofType,
      treeSize: Int,
      leaves: List<IndexedMerkleLeaf>,
      hashes: List<SecureHash>
    ) : MerkleProof

    @Suspendable
    fun createIndexedMerkleLeaf(
        index: Int,
        nonce: ByteArray?,
        leafData: ByteArray
    ) : IndexedMerkleLeaf
}
