package net.corda.ledger.common.flow.impl.transaction.filtered

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.flow.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransactionVerificationException
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.util.Base64
import java.util.Objects

class FilteredTransactionImpl(
    override val id: SecureHash,
    override val topLevelMerkleProof: MerkleProof,
    override val filteredComponentGroups: Map<Int, FilteredComponentGroup>,
    private val jsonMarshallingService: JsonMarshallingService,
    private val merkleTreeProvider: MerkleTreeProvider
) : FilteredTransaction {

    override fun verify() {
        validate(topLevelMerkleProof.leaves.isNotEmpty()) { "At least one component group merkle leaf is required" }

        val transactionMetadataLeaves = topLevelMerkleProof.leaves.filter { it.index == 0 }

        validate(transactionMetadataLeaves.isNotEmpty()) {
            "Top level Merkle proof does not contain a leaf with index 0"
        }

        validate(transactionMetadataLeaves.size == 1) {
            "Top level Merkle proof contains more than one leaf with index 0"
        }

        val topLevelMerkleProofLeafIndexes = topLevelMerkleProof.leaves.map { it.index }.toSet()
        val filteredComponentGroupIndexes = filteredComponentGroups.keys

        validate(topLevelMerkleProofLeafIndexes == filteredComponentGroupIndexes) {
            "Top level Merkle proof does not contain the same indexes as the filtered component groups"
        }

        validate(filteredComponentGroups[0] != null) { "Component group 0 does not exist" }

        val transactionMetadataProof = filteredComponentGroups[0]!!.merkleProof

        validate(transactionMetadataProof.treeSize == 1) {
            "Component group 0's Merkle proof must have a tree size of 1 but has a size of ${transactionMetadataProof.treeSize}"
        }
        validate(transactionMetadataProof.leaves.size == 1) {
            "Component group 0's Merkle proof must have a single leaf but contains ${transactionMetadataProof.leaves.size}"
        }

        validate(topLevelMerkleProof.verify(id, createTopLevelAuditProofProvider())) {
            "Top level Merkle proof cannot be verified against transaction's id"
        }

        if (filteredComponentGroups.size == 1) {
            return
        }

        val componentGroupDigestAlgorithmName =
            DigestAlgorithmName(metadata.getDigestSettings()[COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY] as String)

        val componentGroupAuditProofProvider = createComponentGroupAuditProofProvider(componentGroupDigestAlgorithmName)
        val componentGroupSizeProofProvider = createComponentGroupSizeProofProvider(componentGroupDigestAlgorithmName)

        for ((componentGroupIndex, filteredComponentGroup) in filteredComponentGroups) {

            val componentGroupFromTopLevelProofLeafData =
                topLevelMerkleProof.leaves.single { it.index == componentGroupIndex }.leafData

            val componentLeafHash =
                SecureHash(componentGroupDigestAlgorithmName.name, componentGroupFromTopLevelProofLeafData)

            val providerToVerifyWith = when (filteredComponentGroup.merkleProof.proofType) {
                MerkleProofType.AUDIT -> componentGroupAuditProofProvider
                MerkleProofType.SIZE -> componentGroupSizeProofProvider
            }
            validate(filteredComponentGroup.merkleProof.verify(componentLeafHash, providerToVerifyWith)) {
                "Component group leaf [index = $componentGroupIndex] Merkle proof cannot be verified against the top level Merkle " +
                        "tree's leaf with the same index"
            }
        }
    }

    override val metadata: TransactionMetadata by lazy {
        val proof = checkNotNull(filteredComponentGroups[0]?.merkleProof) { "Component group 0's Merkle proof does not exist" }
        check(proof.leaves.size == 1) { "Component group 0's Merkle proof must have a single leaf but contains ${proof.leaves.size}" }
        jsonMarshallingService.parse(proof.leaves.single().leafData.decodeToString(), TransactionMetadataImpl::class.java)
    }

    override fun getComponentGroupContent(componentGroupIndex: Int): List<Pair<Int, ByteArray>>? {
        return filteredComponentGroups[componentGroupIndex]?.merkleProof?.leaves?.map { it.index to it.leafData }
    }

    private fun createTopLevelAuditProofProvider(): MerkleTreeHashDigestProvider {
        val digestSettings = metadata.getDigestSettings()
        return merkleTreeProvider.createHashDigestProvider(
            merkleTreeHashDigestProviderName = digestSettings[ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY] as String,
            DigestAlgorithmName(digestSettings[ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY] as String),
            options = mapOf(
                HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to Base64.getDecoder()
                    .decode(digestSettings[ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY] as String),
                HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to Base64.getDecoder()
                    .decode(digestSettings[ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY] as String)
            )
        )
    }

    private fun createComponentGroupAuditProofProvider(
        componentGroupDigestAlgorithmName: DigestAlgorithmName
    ): MerkleTreeHashDigestProvider {
        return merkleTreeProvider.createHashDigestProvider(
            merkleTreeHashDigestProviderName = HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME,
            componentGroupDigestAlgorithmName
        )
    }

    private fun createComponentGroupSizeProofProvider(
        componentGroupDigestAlgorithmName: DigestAlgorithmName
    ): MerkleTreeHashDigestProvider {
        return merkleTreeProvider.createHashDigestProvider(
            merkleTreeHashDigestProviderName = HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME,
            componentGroupDigestAlgorithmName
        )
    }

    private inline fun validate(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw FilteredTransactionVerificationException(id, message())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FilteredTransactionImpl

        if (id != other.id) return false
        if (topLevelMerkleProof != other.topLevelMerkleProof) return false
        if (filteredComponentGroups != other.filteredComponentGroups) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(id, topLevelMerkleProof, filteredComponentGroups)
    }

    override fun toString(): String {
        return "FilteredTransactionImpl(id=$id, topLevelMerkleProof=$topLevelMerkleProof, filteredComponentGroups=$filteredComponentGroups)"
    }
}