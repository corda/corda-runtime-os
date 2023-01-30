package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.util.Base64

fun TransactionWithMetadata.getDigestSetting(settingKey: String): String {
    return metadata.getDigestSettings()[settingKey]!!
}
val TransactionWithMetadata.notaryMerkleTreeDigestProviderName
    get() = getDigestSetting(NOTARY_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

val TransactionWithMetadata.notaryMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            NOTARY_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

val TransactionWithMetadata.notaryMerkleTreeDigestOptionsLeafPrefixB64
    get() = getDigestSetting(NOTARY_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY)

val TransactionWithMetadata.notaryMerkleTreeDigestOptionsLeafPrefix: ByteArray
    get() = Base64.getDecoder().decode(notaryMerkleTreeDigestOptionsLeafPrefixB64)

val TransactionWithMetadata.notaryMerkleTreeDigestOptionsNodePrefixB64
    get() = getDigestSetting(NOTARY_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY)

val TransactionWithMetadata.notaryMerkleTreeDigestOptionsNodePrefix: ByteArray
    get() = Base64.getDecoder().decode(notaryMerkleTreeDigestOptionsNodePrefixB64)

fun TransactionWithMetadata.getNotaryMerkleTreeDigestProvider(merkleTreeProvider: MerkleTreeProvider): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        notaryMerkleTreeDigestProviderName,
        notaryMerkleTreeDigestAlgorithmName,
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to notaryMerkleTreeDigestOptionsLeafPrefix,
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to notaryMerkleTreeDigestOptionsNodePrefix
        )
    )