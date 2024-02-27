package net.corda.ledger.common.flow.impl.transaction

import net.corda.ledger.common.data.transaction.BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestAlgorithmName
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsLeafPrefix
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsLeafPrefixB64
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsNodePrefix
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsNodePrefixB64
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestProviderName
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsLeafPrefix
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsLeafPrefixB64
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsNodePrefix
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsNodePrefixB64
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.merkle.HashDigestConstants
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata

const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "batchMerkleTreeDigestProviderName"
const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "batchMerkleTreeDigestAlgorithmName"
const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsLeafPrefixB64"
const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsNodePrefixB64"

fun TransactionWithMetadata.getBatchSignatureMetadataSettings(): Map<String, String> = mapOf(
    SIGNATURE_BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to this.metadata.batchMerkleTreeDigestProviderName,
    SIGNATURE_BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to this.metadata.batchMerkleTreeDigestAlgorithmName.name,
    SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to this.metadata.batchMerkleTreeDigestOptionsLeafPrefixB64,
    SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to this.metadata.batchMerkleTreeDigestOptionsNodePrefixB64
)

fun List<TransactionWithMetadata>.confirmHashPrefixesAreDifferent() {
    require(
        this.none { it.metadata.batchMerkleTreeDigestOptionsLeafPrefix contentEquals it.metadata.rootMerkleTreeDigestOptionsLeafPrefix }
    ) {
        "The transaction can be batch signed only if the leaf prefixes for its root and the batch tree are different."
    }
    require(
        this.none { it.metadata.batchMerkleTreeDigestOptionsNodePrefix contentEquals it.metadata.rootMerkleTreeDigestOptionsNodePrefix }
    ) {
        "The transaction can be batch signed only if the node prefixes for its root and the batch tree are different."
    }
}

fun List<TransactionWithMetadata>.confirmBatchSigningRequirements() {
    require(this.isNotEmpty()) { "Cannot sign empty batch." }
    require(this.all { it.metadata.batchMerkleTreeDigestProviderName == HashDigestConstants.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME }) {
        "Batch signature supports only ${HashDigestConstants.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME}."
    }
    require(this.map { it.metadata.batchMerkleTreeDigestAlgorithmName }.distinct().size == 1) {
        "Batch merkle tree digest algorithm names should be the same in a batch to be signed."
    }
    require(this.map { it.metadata.batchMerkleTreeDigestOptionsLeafPrefix }.distinct().size == 1) {
        "Batch merkle tree digest leaf prefixes should be the same in a batch to be signed."
    }
    require(this.map { it.metadata.batchMerkleTreeDigestOptionsNodePrefix }.distinct().size == 1) {
        "Batch merkle tree digest node prefixes should be the same in a batch to be signed."
    }
    require(
        this.none {
            it.metadata.batchMerkleTreeDigestOptionsLeafPrefix contentEquals it.metadata.batchMerkleTreeDigestOptionsNodePrefix
        }
    ) {
        "Batch merkle tree digest node prefixes and leaf prefixes need to be different for each this."
    }

    require(
        this.all {
            it.metadata.batchMerkleTreeDigestOptionsLeafPrefixB64 contentEquals
                WireTransactionDigestSettings.defaultValues[BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY]
        }
    ) {
        "Batch merkle tree digest leaf prefixes can only be the default values."
    }
    require(
        this.all {
            it.metadata.batchMerkleTreeDigestOptionsNodePrefixB64 contentEquals
                WireTransactionDigestSettings.defaultValues[BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY]
        }
    ) {
        "Batch merkle tree digest node prefixes can only be the default values."
    }
    require(
        this.all {
            it.metadata.rootMerkleTreeDigestOptionsLeafPrefixB64 contentEquals
                WireTransactionDigestSettings.defaultValues[ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY]
        }
    ) {
        "Root merkle tree digest leaf prefixes can only be the default values."
    }
    require(
        this.all {
            it.metadata.rootMerkleTreeDigestOptionsNodePrefixB64 contentEquals
                WireTransactionDigestSettings.defaultValues[ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY]
        }
    ) {
        "Root merkle tree digest node prefixes can only be the default values."
    }

    // TODO CORE-6615 This check needs to be reconsidered in the future
    val algorithms = this.map { it.id.algorithm }.distinct()
    require(algorithms.size == 1) {
        "Cannot sign a batch with multiple hash algorithms: $algorithms"
    }
    this.confirmHashPrefixesAreDifferent()
}

fun TransactionWithMetadata.confirmBatchMerkleSettingsMatch(
    signatureWithMetadata: DigitalSignatureAndMetadata
) {
    require(this.metadata.batchMerkleTreeDigestProviderName == signatureWithMetadata.batchMerkleTreeDigestProviderName) {
        "Batch signature digest provider should match with the transaction batch merkle digest provider."
    }
    require(this.metadata.batchMerkleTreeDigestAlgorithmName == signatureWithMetadata.batchMerkleTreeDigestAlgorithmName) {
        "Batch signature algorithm should match with the transaction batch merkle algorithm name."
    }
    require(
        this.metadata.batchMerkleTreeDigestOptionsLeafPrefix contentEquals
            signatureWithMetadata.batchMerkleTreeDigestOptionsLeafPrefix
    ) {
        "Batch signature leaf prefix should match with the transaction batch merkle leaf prefix."
    }
    require(
        this.metadata.batchMerkleTreeDigestOptionsNodePrefix contentEquals
            signatureWithMetadata.batchMerkleTreeDigestOptionsNodePrefix
    ) {
        "Batch signature node prefix should match with the transaction batch merkle node prefix."
    }
}
