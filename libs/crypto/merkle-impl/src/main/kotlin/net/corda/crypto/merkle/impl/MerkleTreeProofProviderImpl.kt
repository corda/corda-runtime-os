package net.corda.crypto.merkle.impl

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProofProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ MerkleTreeProofProvider::class],
    scope = ServiceScope.PROTOTYPE
)
class MerkleTreeProofProviderImpl : MerkleTreeProofProvider {
    override fun createMerkleProof(proofType: MerkleProofType, treeSize: Int, leaves: List<IndexedMerkleLeaf>,
        hashes: List<SecureHash>
    ): MerkleProof  =
        MerkleProofImpl(proofType, treeSize, leaves, hashes)

    override fun createIndexedMerkleLeaf(index: Int, nonce: ByteArray?, leafData: ByteArray): IndexedMerkleLeaf
        = IndexedMerkleLeafImpl(index, nonce, leafData)
}