package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import java.util.Base64

fun DigitalSignatureAndMetadata.getDigestSetting(settingKey: String): String {
    return metadata.properties[settingKey]!!
}
val DigitalSignatureAndMetadata.batchMerkleTreeDigestProviderName
    get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

val DigitalSignatureAndMetadata.batchMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

val DigitalSignatureAndMetadata.batchMerkleTreeDigestOptionsLeafPrefix: ByteArray
    get() = Base64.getDecoder().decode(getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY))

val DigitalSignatureAndMetadata.batchMerkleTreeDigestOptionsNodePrefix: ByteArray
    get() = Base64.getDecoder().decode(getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY))

fun DigitalSignatureAndMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider: MerkleTreeProvider): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        batchMerkleTreeDigestProviderName,
        batchMerkleTreeDigestAlgorithmName,
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to batchMerkleTreeDigestOptionsLeafPrefix,
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to batchMerkleTreeDigestOptionsNodePrefix
        )
    )