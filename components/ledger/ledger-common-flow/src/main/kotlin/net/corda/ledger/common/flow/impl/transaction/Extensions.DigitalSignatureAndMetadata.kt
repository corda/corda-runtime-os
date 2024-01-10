package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import java.util.Base64

private val base64Decoder = Base64.getDecoder()

fun DigitalSignatureAndMetadata.getMetadata(settingKey: String): String {
    return requireNotNull(metadata.properties[settingKey]) {
        "'$settingKey' is not available in the metadata of the signature."
    }
}

val DigitalSignatureAndMetadata.batchMerkleTreeDigestProviderName
    get() = getMetadata(SIGNATURE_BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

val DigitalSignatureAndMetadata.batchMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getMetadata(
            SIGNATURE_BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

val DigitalSignatureAndMetadata.batchMerkleTreeDigestOptionsLeafPrefix: ByteArray
    get() = base64Decoder.decode(getMetadata(SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY))

val DigitalSignatureAndMetadata.batchMerkleTreeDigestOptionsNodePrefix: ByteArray
    get() = base64Decoder.decode(getMetadata(SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY))

fun DigitalSignatureAndMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider: MerkleTreeProvider): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        batchMerkleTreeDigestProviderName,
        batchMerkleTreeDigestAlgorithmName,
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to batchMerkleTreeDigestOptionsLeafPrefix,
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to batchMerkleTreeDigestOptionsNodePrefix
        )
    )