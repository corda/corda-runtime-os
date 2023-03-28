package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.ledger.common.data.transaction.BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestAlgorithmName
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsLeafPrefix
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsLeafPrefixB64
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsNodePrefix
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestOptionsNodePrefixB64
import net.corda.ledger.common.data.transaction.batchMerkleTreeDigestProviderName
import net.corda.ledger.common.data.transaction.getBatchMerkleTreeDigestProvider
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsLeafPrefix
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsLeafPrefixB64
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsNodePrefix
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsNodePrefixB64
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey
import java.time.Instant

const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "batchMerkleTreeDigestProviderName"
const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "batchMerkleTreeDigestAlgorithmName"
const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsLeafPrefixB64"
const val SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsNodePrefixB64"

@Suppress("Unused", "LongParameterList")
@Component(
    service = [TransactionSignatureService::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class TransactionSignatureServiceImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = DigitalSignatureVerificationService::class)
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    @Reference(service = SignatureSpecService::class)
    private val signatureSpecService: SignatureSpecService,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = MemberLookup::class)
    private val memberLookup: MemberLookup,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : TransactionSignatureService, SingletonSerializeAsToken, UsedByFlow {

    @Suspendable
    override fun sign(
        transaction: TransactionWithMetadata,
        publicKeys: Iterable<PublicKey>
    ): List<DigitalSignatureAndMetadata> {
        return getAvailableKeysFor(publicKeys).map { publicKey ->
            val signatureSpec = requireNotNull(signatureSpecService.defaultSignatureSpec(publicKey)) {
                "There are no available signature specs for this public key. ($publicKey ${publicKey.algorithm})"
            }
            val signatureMetadata = getSignatureMetadata(signatureSpec)
            val signableData = SignableData(transaction.id, signatureMetadata)
            val signature = signingService.sign(
                serializationService.serialize(signableData).bytes,
                publicKey,
                signatureSpec
            )
            DigitalSignatureAndMetadata(signature, signatureMetadata)
        }
    }

    @Suspendable
    override fun signBatch(
        transactions: List<TransactionWithMetadata>,
        publicKeys: Iterable<PublicKey>
    ): List<List<DigitalSignatureAndMetadata>> {
        confirmBatchSigningRequirements(transactions)

        val publicKeysToSigSpecs = getAvailableKeysFor(publicKeys).associateWith { publicKey ->
            requireNotNull(signatureSpecService.defaultSignatureSpec(publicKey)) {
                "There are no available signature specs for this public key. ($publicKey ${publicKey.algorithm})"
            }
        }

        val hashDigestProvider = transactions.first().getBatchMerkleTreeDigestProvider(merkleTreeProvider)
        val batchTree = merkleTreeProvider.createTree(transactions.map { it.id.bytes }, hashDigestProvider)

        val batchSignaturesWithMeta = publicKeysToSigSpecs.map { (publicKey, signatureSpec) ->
            val signatureMetadata =
                getSignatureMetadata(signatureSpec, getBatchSignatureMetadataSettings(transactions.first()))
            val signableData = SignableData(batchTree.root, signatureMetadata)

            signingService.sign(
                serializationService.serialize(signableData).bytes,
                publicKey,
                signatureSpec
            ) to signatureMetadata
        }
        return List(transactions.size) {
            val proof = batchTree.createAuditProof(listOf(it))
            batchSignaturesWithMeta.map { (signature, signatureMetadata) ->
                DigitalSignatureAndMetadata(signature, signatureMetadata, proof)
            }
        }
    }

    override fun verifySignature(
        transaction: TransactionWithMetadata,
        signatureWithMetadata: DigitalSignatureAndMetadata
    ) {
        // TODO needs to be reviewed by Lajos
        val keyUsedToSign = memberLookup.lookup(signatureWithMetadata.by)
        val signatureSpec =
            checkAndGetSignatureSpec(
                signatureWithMetadata.metadata.signatureSpec,
                keyUsedToSign
            )

        val proof = signatureWithMetadata.proof
        val signedHash = if (proof == null) {   // Simple signature
            transaction.id
        } else {                                // Batch signature

            confirmSignatureBatchMerkleSettingsMatchWithTransactions(transaction, signatureWithMetadata)
            confirmHashPrefixesAreDifferent(listOf(transaction))

            require(proof.leaves.filter { transaction.id.bytes contentEquals it.leafData }.size == 1) {
                "The transaction id cannot be found in the provided Merkle proof for the batch signature" +
                        " - the signature cannot be verified."
            }
            val hashDigestProvider = signatureWithMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider)
            proof.calculateRoot(hashDigestProvider)
        }

        val signedData = SignableData(signedHash, signatureWithMetadata.metadata)

        return digitalSignatureVerificationService.verify(
            serializationService.serialize(signedData).bytes,
            signatureWithMetadata.signature.bytes,
            keyUsedToSign,
            signatureSpec
        )
    }

    @Suspendable
    private fun MemberLookup.lookup(keyId: SecureHash): PublicKey {
        val digestAlgorithmOfKeyId = keyId.algorithm
        val knownKeysByKeyIds = lookup().flatMap {
            it.ledgerKeys
        }.associateBy {
            it.fullIdHash(keyEncodingService, digestService, digestAlgorithmOfKeyId)
        }

        return knownKeysByKeyIds[keyId] ?: error("Member for consensual signature not found")
    }

    private fun confirmBatchSigningRequirements(transactions: List<TransactionWithMetadata>) {
        require(transactions.isNotEmpty()) { "Cannot sign empty batch." }
        require(transactions.all { it.batchMerkleTreeDigestProviderName == HASH_DIGEST_PROVIDER_TWEAKABLE_NAME }) {
            "Batch signature supports only $HASH_DIGEST_PROVIDER_TWEAKABLE_NAME."
        }
        require(transactions.map { it.batchMerkleTreeDigestAlgorithmName }.distinct().size == 1) {
            "Batch merkle tree digest algorithm names should be the same in a batch to be signed."
        }
        require(transactions.map { it.batchMerkleTreeDigestOptionsLeafPrefix }.distinct().size == 1) {
            "Batch merkle tree digest leaf prefixes should be the same in a batch to be signed."
        }
        require(transactions.map { it.batchMerkleTreeDigestOptionsNodePrefix }.distinct().size == 1) {
            "Batch merkle tree digest node prefixes should be the same in a batch to be signed."
        }
        require(transactions.none {
            it.batchMerkleTreeDigestOptionsLeafPrefix contentEquals it.batchMerkleTreeDigestOptionsNodePrefix
        }) {
            "Batch merkle tree digest node prefixes and leaf prefixes need to be different for each transactions."
        }

        require(transactions.all {
            it.batchMerkleTreeDigestOptionsLeafPrefixB64 contentEquals
                    WireTransactionDigestSettings.defaultValues[BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY]
        }) {
            "Batch merkle tree digest leaf prefixes can only be the default values."
        }
        require(transactions.all {
            it.batchMerkleTreeDigestOptionsNodePrefixB64 contentEquals
                    WireTransactionDigestSettings.defaultValues[BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY]
        }) {
            "Batch merkle tree digest node prefixes can only be the default values."
        }
        require(transactions.all {
            it.rootMerkleTreeDigestOptionsLeafPrefixB64 contentEquals
                    WireTransactionDigestSettings.defaultValues[ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY]
        }) {
            "Root merkle tree digest leaf prefixes can only be the default values."
        }
        require(transactions.all {
            it.rootMerkleTreeDigestOptionsNodePrefixB64 contentEquals
                    WireTransactionDigestSettings.defaultValues[ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY]
        }) {
            "Root merkle tree digest node prefixes can only be the default values."
        }

        // TODO CORE-6615 This check needs to be reconsidered in the future
        val algorithms = transactions.map { it.id.algorithm }.distinct()
        require(algorithms.size == 1) {
            "Cannot sign a batch with multiple hash algorithms: $algorithms"
        }
        confirmHashPrefixesAreDifferent(transactions)
    }

    private fun confirmHashPrefixesAreDifferent(transactions: List<TransactionWithMetadata>) {
        require(transactions.none { it.batchMerkleTreeDigestOptionsLeafPrefix contentEquals it.rootMerkleTreeDigestOptionsLeafPrefix }) {
            "The transaction can be batch signed only if the leaf prefixes for its root and the batch tree are different."
        }
        require(transactions.none { it.batchMerkleTreeDigestOptionsNodePrefix contentEquals it.rootMerkleTreeDigestOptionsNodePrefix }) {
            "The transaction can be batch signed only if the node prefixes for its root and the batch tree are different."
        }
    }

    @Suspendable
    private fun getAvailableKeysFor(publicKeys: Iterable<PublicKey>): List<PublicKey> {
        val availableKeys = signingService.findMySigningKeys(publicKeys.toSet()).values.filterNotNull()
        if (availableKeys.isEmpty()) {
            throw (TransactionNoAvailableKeysException(
                "The publicKeys do not have any private counterparts available.",
                null
            ))
        }
        return availableKeys
    }

    private fun confirmSignatureBatchMerkleSettingsMatchWithTransactions(
        transaction: TransactionWithMetadata,
        signatureWithMetadata: DigitalSignatureAndMetadata
    ) {
        require(transaction.batchMerkleTreeDigestProviderName == signatureWithMetadata.batchMerkleTreeDigestProviderName) {
            "Batch signature digest provider should match with the transaction batch merkle digest provider."
        }
        require(transaction.batchMerkleTreeDigestAlgorithmName == signatureWithMetadata.batchMerkleTreeDigestAlgorithmName) {
            "Batch signature algorithm should match with the transaction batch merkle algorithm name."
        }
        require(
            transaction.batchMerkleTreeDigestOptionsLeafPrefix contentEquals
                    signatureWithMetadata.batchMerkleTreeDigestOptionsLeafPrefix
        ) {
            "Batch signature leaf prefix should match with the transaction batch merkle leaf prefix."
        }
        require(
            transaction.batchMerkleTreeDigestOptionsNodePrefix contentEquals
                    signatureWithMetadata.batchMerkleTreeDigestOptionsNodePrefix
        ) {
            "Batch signature node prefix should match with the transaction batch merkle node prefix."
        }
    }

    private fun checkAndGetSignatureSpec(signatureSpec: SignatureSpec, signingKey: PublicKey): SignatureSpec {
        val compatibleSpecs = signatureSpecService.compatibleSignatureSpecs(signingKey)
        require(signatureSpec in compatibleSpecs) {
            "The signature spec in the signature metadata ('$signatureSpec') is incompatible with its key!"
        }
        return signatureSpec
    }

    private fun getSignatureMetadata(
        signatureSpec: SignatureSpec,
        batchSettings: Map<String, String> = emptyMap()
    ): DigitalSignatureMetadata {
        val cpiSummary = getCpiSummary()
        return DigitalSignatureMetadata(
            Instant.now(),
            signatureSpec,
            mapOf(
                "platformVersion" to platformInfoProvider.activePlatformVersion.toString(),
                "cpiName" to cpiSummary.name,
                "cpiVersion" to cpiSummary.version,
                "cpiSignerSummaryHash" to cpiSummary.signerSummaryHash.toString()
            ) + batchSettings
        )
    }

    private fun getBatchSignatureMetadataSettings(transaction: TransactionWithMetadata): Map<String, String> = mapOf(
        SIGNATURE_BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to transaction.batchMerkleTreeDigestProviderName,
        SIGNATURE_BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to transaction.batchMerkleTreeDigestAlgorithmName.name,
        SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to transaction.batchMerkleTreeDigestOptionsLeafPrefixB64,
        SIGNATURE_BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to transaction.batchMerkleTreeDigestOptionsNodePrefixB64
    )
}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummaryImpl(
        name = "CPI name",
        version = "CPI version",
        signerSummaryHash = SecureHashImpl("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHashImpl("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )

private fun PublicKey.fullIdHash(
    keyEncodingService: KeyEncodingService,
    digestService: DigestService,
    digestAlgorithmName: String
): SecureHash =
    digestService.hash(keyEncodingService.encodeAsByteArray(this), DigestAlgorithmName(digestAlgorithmName))
