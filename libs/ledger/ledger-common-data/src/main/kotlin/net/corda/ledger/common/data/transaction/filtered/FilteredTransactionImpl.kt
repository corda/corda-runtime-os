package net.corda.ledger.common.data.transaction.filtered

import net.corda.ledger.common.data.transaction.COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.parse
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleProof
import java.util.Base64

class FilteredTransactionImpl(
    override val id: SecureHash,
    override val componentGroupMerkleProof: MerkleProof,
    override val filteredComponentGroups: Map<Int, FilteredComponentGroup>,
    private val jsonMarshallingService: JsonMarshallingService,
    private val merkleTreeProvider: MerkleTreeProvider
) : FilteredTransaction {

    override fun verify() {
        validate(componentGroupMerkleProof.leaves.isNotEmpty()) { "At least one component group merkle leaf is required" }

        val transactionMetadataLeaves = componentGroupMerkleProof.leaves.filter { it.index == 0 }

        validate(transactionMetadataLeaves.isNotEmpty()) {
            "Top level Merkle proof does not contain a leaf with index 0"
        }

        validate(transactionMetadataLeaves.size == 1) {
            "Top level Merkle proof contains more than one leaf with index 0"
        }

        val componentGroupMerkleProofLeafIndexes = componentGroupMerkleProof.leaves.map { it.index }.toSet()
        val filteredComponentGroupIndexes = filteredComponentGroups.keys

        validate(componentGroupMerkleProofLeafIndexes == filteredComponentGroupIndexes) {
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

        validate(componentGroupMerkleProof.verify(id, createRootAuditProofProvider())) {
            "Top level Merkle proof cannot be verified against transaction's id"
        }

        if (filteredComponentGroups.size == 1) {
            return
        }

        val componentGroupDigestAlgorithmName =
            DigestAlgorithmName(metadata.getDigestSettings()[COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY] as String)

        val componentGroupAuditProofProvider = createComponentGroupAuditProofProvider(componentGroupDigestAlgorithmName)
        val componentGroupSizeProofProvider = createComponentGroupSizeProofProvider(componentGroupDigestAlgorithmName)

        for ((componentGroupOrdinal, filteredComponentGroup) in filteredComponentGroups) {

            val componentGroupFromTopLevelProofLeafData =
                componentGroupMerkleProof.leaves.single { it.index == componentGroupOrdinal }.leafData

            val componentLeafHash = SecureHash(componentGroupDigestAlgorithmName.name, componentGroupFromTopLevelProofLeafData)

            val providerToVerifyWith = when (filteredComponentGroup.merkleProofType) {
                MerkleProofType.AUDIT -> {
                    componentGroupAuditProofProvider
                }
                MerkleProofType.SIZE -> {
                    if (filteredComponentGroup.merkleProof.treeSize == 1 && filteredComponentGroup.merkleProof.leaves.single().leafData.isEmpty()) {
                        componentGroupAuditProofProvider
                    } else {
                        componentGroupSizeProofProvider
                    }
                }
            }
            validate(filteredComponentGroup.merkleProof.verify(componentLeafHash, providerToVerifyWith)) {
                "Component group leaf [index = $componentGroupOrdinal] Merkle proof cannot be verified against the top level Merkle " +
                        "tree's leaf with the same index"
            }
        }
    }

    override val metadata: TransactionMetadata by lazy {
        val proof = checkNotNull(filteredComponentGroups[0]?.merkleProof) { "Component group 0's Merkle proof does not exist" }
        check(proof.leaves.size == 1) { "Component group 0's Merkle proof must have a single leaf but contains ${proof.leaves.size}" }
        jsonMarshallingService.parse(proof.leaves.single().leafData.decodeToString())
    }

    override fun getComponentGroupContent(componentGroupOrdinal: Int): List<ByteArray>? {
        return filteredComponentGroups[componentGroupOrdinal]?.merkleProof?.leaves?.map { it.leafData }
    }

    private fun createRootAuditProofProvider(): MerkleTreeHashDigestProvider {
        val digestSettings = metadata.getDigestSettings()
        return merkleTreeProvider.createHashDigestProvider(
            merkleTreeHashDigestProviderName = HASH_DIGEST_PROVIDER_TWEAKABLE_NAME, // TODO should come from meta eventually
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
}

internal class FilteredTransactionVerificationException(id: SecureHash, reason: String) : CordaRuntimeException(
    "Failed to verify filtered transaction $id. Reason: $reason"
)