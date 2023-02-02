package net.corda.ledger.common.data.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.util.Base64

private val base64Decoder = Base64.getDecoder()

fun TransactionWithMetadata.getDigestSetting(settingKey: String): String {
    return requireNotNull(metadata.getDigestSettings()[settingKey]) {
        "'$settingKey' digest setting is not available in the metadata of the transaction."
    }
}

val TransactionWithMetadata.batchMerkleTreeDigestProviderName
    get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

val TransactionWithMetadata.batchMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

val TransactionWithMetadata.batchMerkleTreeDigestOptionsLeafPrefixB64
    get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY)

val TransactionWithMetadata.batchMerkleTreeDigestOptionsLeafPrefix: ByteArray
    get() = base64Decoder.decode(batchMerkleTreeDigestOptionsLeafPrefixB64)

val TransactionWithMetadata.batchMerkleTreeDigestOptionsNodePrefixB64
    get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY)

val TransactionWithMetadata.batchMerkleTreeDigestOptionsNodePrefix: ByteArray
    get() = base64Decoder.decode(batchMerkleTreeDigestOptionsNodePrefixB64)

fun TransactionWithMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider: MerkleTreeProvider): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        batchMerkleTreeDigestProviderName,
        batchMerkleTreeDigestAlgorithmName,
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to batchMerkleTreeDigestOptionsLeafPrefix,
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to batchMerkleTreeDigestOptionsNodePrefix
        )
    )

val TransactionWithMetadata.rootMerkleTreeDigestProviderName
    get() =
        getDigestSetting(ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

val TransactionWithMetadata.rootMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

val TransactionWithMetadata.rootMerkleTreeDigestOptionsLeafPrefix: ByteArray
    get() = Base64.getDecoder()
        .decode(getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY))

val TransactionWithMetadata.rootMerkleTreeDigestOptionsNodePrefix: ByteArray
    get() = Base64.getDecoder()
        .decode(getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY))

fun TransactionWithMetadata.getRootMerkleTreeDigestProvider(merkleTreeProvider: MerkleTreeProvider): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        rootMerkleTreeDigestProviderName,
        rootMerkleTreeDigestAlgorithmName,
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to rootMerkleTreeDigestOptionsLeafPrefix,
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to rootMerkleTreeDigestOptionsNodePrefix
        )
    )