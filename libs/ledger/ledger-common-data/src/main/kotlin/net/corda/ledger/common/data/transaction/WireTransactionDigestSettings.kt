package net.corda.ledger.common.data.transaction

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import java.util.Base64

const val NOTARY_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "notaryMerkleTreeDigestProviderName"
const val NOTARY_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "notaryMerkleTreeDigestAlgorithmName"
const val NOTARY_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "notaryMerkleTreeDigestOptionsLeafPrefixB64"
const val NOTARY_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "notaryMerkleTreeDigestOptionsNodePrefixB64"

const val ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "rootMerkleTreeDigestProviderName"
const val ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "rootMerkleTreeDigestAlgorithmName"
const val ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "rootMerkleTreeDigestOptionsLeafPrefixB64"
const val ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "rootMerkleTreeDigestOptionsNodePrefixB64"

const val COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "componentMerkleTreeDigestProviderName"
const val COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "componentMerkleTreeDigestAlgorithmName"
const val COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY = "componentMerkleTreeEntropyAlgorithmName"

class WireTransactionDigestSettings {
    companion object {
        val defaultValues = linkedMapOf(
            NOTARY_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
            NOTARY_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,

            NOTARY_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to
                    Base64.getEncoder().encodeToString("notaryLeaf".toByteArray(Charsets.UTF_8)),
            NOTARY_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to
                    Base64.getEncoder().encodeToString("notaryNode".toByteArray(Charsets.UTF_8)),


            ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
            ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,

            ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to
                    Base64.getEncoder().encodeToString("leaf".toByteArray(Charsets.UTF_8)),
            ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to
                    Base64.getEncoder().encodeToString("node".toByteArray(Charsets.UTF_8)),

            COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to HASH_DIGEST_PROVIDER_NONCE_NAME,
            COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,

            COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY to DigestAlgorithmName.SHA2_256D.name,
        )
    }
}