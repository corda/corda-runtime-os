@file:Suppress("MatchingDeclarationName")

package net.corda.ledger.common.data.transaction

import net.corda.common.json.validation.JsonValidator
import net.corda.common.json.validation.WrappedJsonSchema
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.bytes
import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.util.*

class JsonMagic {
    private val header = "corda".toByteArray() + byteArrayOf(8)
    private val openingBrace = '{'.code.toByte()

    /**
     * returns a pair of schemaVersion to decoded json
     * */
    fun consume(byteData: ByteArray): Pair<Int?, String?> {
        val hasHeader = byteData.size > header.size && byteData.sliceArray(header.indices).contentEquals(header)
        val hasNotHeader = byteData.isNotEmpty() && byteData[0] == openingBrace

        return if (hasHeader) {
            byteData[header.size].toInt() to byteData.sliceArray(header.size + 1 until byteData.size - 1).decodeToString()
        } else if (hasNotHeader) {
            // no header means schema version 1 which is compatible with platform version <= 5.2.1
            1 to byteData.decodeToString()
        } else {
            null to null
        }
    }
}

object TransactionMetadataUtils {
    fun parseMetadata(
        metadataBytes: ByteArray,
        jsonValidator: JsonValidator,
        jsonMarshallingService: JsonMarshallingService
    ): TransactionMetadataImpl {
        if (metadataBytes.isEmpty()) throw IllegalArgumentException("Metadata is empty.")

        // extracting metadata schema version from a header and json.
        val magic = JsonMagic()
        val (version, json) = magic.consume(metadataBytes)

        requireNotNull(json) {
            "Metadata json is invalid."
        }

        if (version != null) {
//            TODO("if metadataSchemaVersion is null, it means MPV <= 5.2.1. write JSON without a header.")
        }

        jsonValidator.validate(json, getMetadataSchema(jsonValidator))
        val metadata = jsonMarshallingService.parse(json, TransactionMetadataImpl::class.java)
        check(metadata.digestSettings == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${metadata.digestSettings} vs " +
                "${WireTransactionDigestSettings.defaultValues}"
        }
        return metadata
    }

    private fun getMetadataSchema(jsonValidator: JsonValidator): WrappedJsonSchema {
        return jsonValidator.parseSchema(getSchema(TransactionMetadataImpl.SCHEMA_PATH))
    }

    private fun getSchema(path: String) =
        checkNotNull(this::class.java.getResourceAsStream(path)) { "Failed to load JSON schema from $path" }
}

private val base64Decoder = Base64.getDecoder()

fun TransactionMetadata.getDigestSetting(settingKey: String): String {
    return requireNotNull(digestSettings[settingKey]) {
        "'$settingKey' digest setting is not available in the metadata of the transaction."
    }
}

val TransactionMetadata.batchMerkleTreeDigestProviderName
    get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

val TransactionMetadata.batchMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

val TransactionMetadata.batchMerkleTreeDigestOptionsLeafPrefixB64
    get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY)

val TransactionMetadata.batchMerkleTreeDigestOptionsLeafPrefix: ByteArray
    get() = base64Decoder.decode(batchMerkleTreeDigestOptionsLeafPrefixB64)

val TransactionMetadata.batchMerkleTreeDigestOptionsNodePrefixB64
    get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY)

val TransactionMetadata.batchMerkleTreeDigestOptionsNodePrefix: ByteArray
    get() = base64Decoder.decode(batchMerkleTreeDigestOptionsNodePrefixB64)

fun TransactionMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider: MerkleTreeProvider): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        batchMerkleTreeDigestProviderName,
        batchMerkleTreeDigestAlgorithmName,
        mapOf(
            HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to batchMerkleTreeDigestOptionsLeafPrefix,
            HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to batchMerkleTreeDigestOptionsNodePrefix
        )
    )

val TransactionMetadata.rootMerkleTreeDigestProviderName
    get() =
        getDigestSetting(ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

val TransactionMetadata.rootMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

val TransactionMetadata.rootMerkleTreeDigestOptionsLeafPrefix: ByteArray
    get() = base64Decoder.decode(rootMerkleTreeDigestOptionsLeafPrefixB64)

val TransactionMetadata.rootMerkleTreeDigestOptionsLeafPrefixB64: String
    get() = getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY)

val TransactionMetadata.rootMerkleTreeDigestOptionsNodePrefix: ByteArray
    get() = base64Decoder.decode(rootMerkleTreeDigestOptionsNodePrefixB64)

val TransactionMetadata.rootMerkleTreeDigestOptionsNodePrefixB64: String
    get() = getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY)

fun TransactionMetadata.getRootMerkleTreeDigestProvider(merkleTreeProvider: MerkleTreeProvider): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        rootMerkleTreeDigestProviderName,
        rootMerkleTreeDigestAlgorithmName,
        mapOf(
            HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to rootMerkleTreeDigestOptionsLeafPrefix,
            HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to rootMerkleTreeDigestOptionsNodePrefix
        )
    )

val TransactionMetadata.componentMerkleTreeEntropyAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY
        )
    )

val TransactionMetadata.componentMerkleTreeDigestProviderName
    get() = getDigestSetting(
        COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY
    )

val TransactionMetadata.componentMerkleTreeDigestAlgorithmName
    get() = DigestAlgorithmName(
        getDigestSetting(
            COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
        )
    )

fun TransactionMetadata.getComponentGroupEntropy(
    privacySalt: PrivacySalt,
    componentGroupIndexBytes: ByteArray,
    digestService: DigestService
): ByteArray =
    digestService.hash(
        concatByteArrays(privacySalt.bytes, componentGroupIndexBytes),
        componentMerkleTreeEntropyAlgorithmName
    ).bytes

fun TransactionMetadata.getComponentGroupMerkleTreeDigestProvider(
    privacySalt: PrivacySalt,
    componentGroupIndex: Int,
    merkleTreeProvider: MerkleTreeProvider,
    digestService: DigestService
): MerkleTreeHashDigestProvider =
    merkleTreeProvider.createHashDigestProvider(
        componentMerkleTreeDigestProviderName,
        componentMerkleTreeDigestAlgorithmName,
        mapOf(
            HashDigestConstants.HASH_DIGEST_PROVIDER_ENTROPY_OPTION to
                getComponentGroupEntropy(privacySalt, componentGroupIndex.toByteArray(), digestService)
        )
    )
