package net.corda.ledger.common.data.transaction

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import java.util.Base64

const val BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "batchMerkleTreeDigestProviderName"
const val BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "batchMerkleTreeDigestAlgorithmName"
const val BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsLeafPrefixB64"
const val BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsNodePrefixB64"

const val ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "rootMerkleTreeDigestProviderName"
const val ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "rootMerkleTreeDigestAlgorithmName"
const val ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "rootMerkleTreeDigestOptionsLeafPrefixB64"
const val ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "rootMerkleTreeDigestOptionsNodePrefixB64"

const val COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "componentMerkleTreeDigestProviderName"
const val COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "componentMerkleTreeDigestAlgorithmName"
const val COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY = "componentMerkleTreeEntropyAlgorithmName"

class WireTransactionDigestSettings {
    companion object {
        private val base64Encoder: Base64.Encoder = Base64.getEncoder()
        val defaultValues = mapOf(
            BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
            BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,
            BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to
                    base64Encoder.encodeToString("batchLeaf".toByteArray(Charsets.UTF_8)),
            BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to
                    base64Encoder.encodeToString("batchNode".toByteArray(Charsets.UTF_8)),

            ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
            ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,
            ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to
                    base64Encoder.encodeToString("leaf".toByteArray(Charsets.UTF_8)),
            ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to
                    base64Encoder.encodeToString("node".toByteArray(Charsets.UTF_8)),

            COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to HASH_DIGEST_PROVIDER_NONCE_NAME,
            COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,

            COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,
        )
    }
}