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
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.util.*

const val TRANSACTION_METADATA_BYTE = 8

object TransactionMetadataUtils {
    fun parseMetadata(
        metadataBytes: ByteArray,
        jsonValidator: JsonValidator,
        jsonMarshallingService: JsonMarshallingService
    ): TransactionMetadataImpl {
        // extracting a header and json.
        val (minimumPlatformVersion, json) = metadataBytes.extractHeaderMPVAndJson()

        if (minimumPlatformVersion != null) {
//            TODO("if minimumPlatformVersion is null, it means MPV <= 5.2.1. write JSON without a header.")
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

fun ByteArray.extractHeaderMPVAndJson(): Pair<Int?, String> {
    val openingBraceIndex = this.indexOf('{'.code.toByte())
    val trailingBraceIndex = this.indexOf('}'.code.toByte())

    // if byteArray starts with open curly bracket and end with tailing curly bracket. -> no header
    // if byteArray is 8 bytes
    // if byteArray contains "corda"
    // if it contains number that represents tx metadata
    // if it contains minimum platform version ( starting from 0 -> 5.2.1, 1 -> 5.3, 2 -> 5.4 ... )
    // map MPV integer to MPV form -> 50210, 50300, 50400
    // get appropriate version of schema
    // parse metadata

    // there isn't a header - meaning minimumPlatformVersion of this tx metadata <= 5.2.1
    if (openingBraceIndex == 0 && trailingBraceIndex == lastIndex) {
        return null to this.decodeToString()
    }

    try {
        // there is a header
        val headerBytes = this.copyOfRange(0, openingBraceIndex)
        val json = this.copyOfRange(openingBraceIndex, this.lastIndex + 1).decodeToString()

        if (!headerBytes.canDeserializeVersion()) {
            return null to json
        }

        // extract minimumPlatformVersion from a header byte array
        return headerBytes.copyOfRange(4, lastIndex + 1).getOrNull(1)?.toInt() to json
    } catch (e: Exception) {
        throw CordaRuntimeException("Failed to extract jsob blob from byte array $this")
    }
}

fun ByteArray.canDeserializeVersion(): Boolean {
    // the header byte will look like: "corda" + byteArrayOf(8, <minimum platform version starting from 0 being 5.3>)
    val splitIndex = 4
    val firstHalf = this.copyOfRange(0, splitIndex + 1)
    val secondHalf = this.copyOfRange(splitIndex, lastIndex + 1)

    if (this.size != 7) {
        return false
    }
    if (!firstHalf.contentEquals("corda".toByteArray())) {
        return false
    }
    if (secondHalf.getOrNull(0) != TRANSACTION_METADATA_BYTE.toByte()) {
        return false
    }
    // TODO("if MPV in metadata does not match with current MPV, return false")

    return true
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
