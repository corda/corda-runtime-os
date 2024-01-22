package net.corda.ledger.common.data.transaction

import net.corda.common.json.validation.JsonValidator
import net.corda.common.json.validation.WrappedJsonSchema
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.util.*

object TransactionMetadataUtils {

    private const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsLeafPrefixB64"
    private const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsNodePrefixB64"

    fun parseMetadata(
        metadataBytes: ByteArray,
        jsonValidator: JsonValidator,
        jsonMarshallingService: JsonMarshallingService
    ): TransactionMetadataImpl {
        val json = metadataBytes.decodeToString()
        jsonValidator.validate(json, getMetadataSchema(jsonValidator))
        val metadata = jsonMarshallingService.parse(json, TransactionMetadataImpl::class.java)

        check(metadata.digestSettings == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${metadata.digestSettings} vs " +
                    "${WireTransactionDigestSettings.defaultValues}"
        }
        return metadata
    }

    fun createTopLevelDigestProvider(
        metadata: TransactionMetadata,
        merkleTreeProvider: MerkleTreeProvider
    ): MerkleTreeHashDigestProvider {
        val digestSettings = metadata.digestSettings
        return merkleTreeProvider.createHashDigestProvider(
            merkleTreeHashDigestProviderName = digestSettings[ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY] as String,
            DigestAlgorithmName(digestSettings[ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY] as String),
            options = mapOf(
                HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to Base64.getDecoder()
                    .decode(digestSettings[ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY] as String),
                HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to Base64.getDecoder()
                    .decode(digestSettings[ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY] as String)
            )
        )
    }

    fun createHashDigestProvider(
        metadata: TransactionMetadata,
        merkleTreeProvider: MerkleTreeProvider
    ): MerkleTreeHashDigestProvider {

        val batchMerkleTreeDigestProviderName = requireNotNull(
            metadata.digestSettings[BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY]
        )
        val batchMerkleTreeDigestAlgorithmName = DigestAlgorithmName(
            requireNotNull(metadata.digestSettings[BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY])
        )
        val batchMerkleTreeDigestOptionsLeafPrefix = requireNotNull(
            metadata.digestSettings[SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY]
        )
        val batchMerkleTreeDigestOptionsNodePrefix = requireNotNull(
            metadata.digestSettings[SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY]
        )
        return merkleTreeProvider.createHashDigestProvider(
            batchMerkleTreeDigestProviderName,
            batchMerkleTreeDigestAlgorithmName,
            mapOf(
                HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to batchMerkleTreeDigestOptionsLeafPrefix,
                HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to batchMerkleTreeDigestOptionsNodePrefix
            )
        )
    }

    fun getMetadataSchema(jsonValidator: JsonValidator): WrappedJsonSchema {
        return jsonValidator.parseSchema(getSchema(TransactionMetadataImpl.SCHEMA_PATH))
    }

    fun getSchema(path: String) =
        checkNotNull(this::class.java.getResourceAsStream(path)) { "Failed to load JSON schema from $path" }
}