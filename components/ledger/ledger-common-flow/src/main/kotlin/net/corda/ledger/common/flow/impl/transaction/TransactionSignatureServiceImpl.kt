package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.NOTARY_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey
import java.time.Instant
import java.util.Base64

const val BATCH_SIGNATURE_KEY = "batchSignature"

const val BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "notaryMerkleTreeDigestProviderName"
const val BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "notaryMerkleTreeDigestAlgorithmName"
const val BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "notaryMerkleTreeDigestOptionsLeafPrefixB64"
const val BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "notaryMerkleTreeDigestOptionsNodePrefixB64"

@Suppress("Unused", "LongParameterList")
@Component(
    service = [TransactionSignatureService::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE,
    property = [ CORDA_SYSTEM_SERVICE ] //@todo ?
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
) : TransactionSignatureService, SingletonSerializeAsToken, UsedByFlow {
    @Suspendable
    override fun sign(transactionId: SecureHash, publicKeys: Iterable<PublicKey>): List<DigitalSignatureAndMetadata> {
        val availableKeys = signingService.findMySigningKeys(publicKeys.toSet()).values.filterNotNull()
        if (availableKeys.isEmpty()) {
            throw (TransactionNoAvailableKeysException(
                "The publicKeys do not have any private counterparts available.",
                null
            ))
        }
        return availableKeys.map { publicKey ->
            val signatureSpec = signatureSpecService.defaultSignatureSpec(publicKey)
            requireNotNull(signatureSpec) {
                "There are no available signature specs for this public key. ($publicKey ${publicKey.algorithm})"
            }
            val signatureMetadata = getSignatureMetadata(signatureSpec)
            val signableData = SignableData(transactionId, signatureMetadata)
            val signature = signingService.sign(
                serializationService.serialize(signableData).bytes,
                publicKey,
                signatureSpec
            )
            DigitalSignatureAndMetadata(signature, signatureMetadata)
        }
    }

    @Suspendable
    override fun sign(
        transactions: List<TransactionWithMetadata>,
        publicKeys: Iterable<PublicKey>
    ): List<List<DigitalSignatureAndMetadata>> {
        require(transactions.isNotEmpty()) { "Cannot sign empty batch."}
        require(transactions.all { it.notaryMerkleTreeDigestProviderName == HASH_DIGEST_PROVIDER_TWEAKABLE_NAME }) {
            "Batch signature supports only $HASH_DIGEST_PROVIDER_TWEAKABLE_NAME."
        }
        require(transactions.map { it.notaryMerkleTreeDigestAlgorithmName }.distinct().size == 1) {
            "Digest Algorithm names should be the same in a batch to be signed."
        }
        require(transactions.map { it.notaryMerkleTreeDigestOptionsLeafPrefix }.distinct().size == 1) {
            "Digest provider leaf prefixes should be the same in a batch to be signed."
        }
        require(transactions.map { it.notaryMerkleTreeDigestOptionsNodePrefix }.distinct().size == 1) {
            "Digest provider node prefixes should be the same in a batch to be signed."
        }
        val hashDigestProvider = transactions.first().getNotaryMerkleTreeDigestProvider()

        val batchTree = merkleTreeProvider.createTree(transactions.map { it.id.bytes }, hashDigestProvider)

        val availableKeys = signingService.findMySigningKeys(publicKeys.toSet()).values.filterNotNull()
        if (availableKeys.isEmpty()) {
            throw (TransactionNoAvailableKeysException(
                "The publicKeys do not have any private counterparts available.",
                null
            ))
        }

        val batchSignaturesWithMeta = availableKeys.map { publicKey ->
                val signatureSpec = signatureSpecService.defaultSignatureSpec(publicKey)
                requireNotNull(signatureSpec) {
                    "There are no available signature specs for this public key. ($publicKey ${publicKey.algorithm})"
                }

                val signatureMetadata =
                    getSignatureMetadata(signatureSpec, getBatchSignatureMetadataSettings(transactions.first()))
                val signableData = SignableData(batchTree.root, signatureMetadata)

                signingService.sign(
                    serializationService.serialize(signableData).bytes,
                    publicKey,
                    signatureSpec
                ) to signatureMetadata
            }
        val proofs = List(transactions.size) {
            batchTree.createAuditProof(listOf(it))
        }
        return List(transactions.size) {
            batchSignaturesWithMeta.map { (signature, signatureMetadata) ->
                DigitalSignatureAndMetadata(signature, signatureMetadata, proofs[it])
            }
        }
    }

    override fun verifySignature(transaction: TransactionWithMetadata, signatureWithMetadata: DigitalSignatureAndMetadata) {
        val signatureSpec = checkAndGetSignatureSpec(signatureWithMetadata)

        if (signatureWithMetadata.metadata.properties.getOrDefault(BATCH_SIGNATURE_KEY, "false") == "false"){
            val signedData = SignableData(transaction.id, signatureWithMetadata.metadata)
            return digitalSignatureVerificationService.verify(
                publicKey = signatureWithMetadata.by,
                signatureSpec = signatureSpec,
                signatureData = signatureWithMetadata.signature.bytes,
                clearData = serializationService.serialize(signedData).bytes
            )
        }

        requireNotNull(signatureWithMetadata.proof) {
            "Batch signature should have a Merkle Proof attached."
        }

        require(transaction.notaryMerkleTreeDigestProviderName == signatureWithMetadata.batchMerkleTreeDigestProviderName ) {
            "Batch signature's digest provider should match with the transaction's notary merkle digest provider."
        }
        require(transaction.notaryMerkleTreeDigestAlgorithmName == signatureWithMetadata.batchMerkleTreeDigestAlgorithmName) {
            "Batch signature's algorithm should match with the transaction's notary merkle algorithm name."
        }
        require(transaction.notaryMerkleTreeDigestOptionsLeafPrefix contentEquals
                signatureWithMetadata.batchMerkleTreeDigestOptionsLeafPrefix) {
            "Batch signature's leaf prefix should match with the transaction's notary merkle leaf prefix."
        }
        require(transaction.notaryMerkleTreeDigestOptionsNodePrefix contentEquals
                signatureWithMetadata.batchMerkleTreeDigestOptionsNodePrefix) {
            "Batch signature's node prefix should match with the transaction's notary merkle node prefix."
        }

        require(signatureWithMetadata.proof!!.leaves.filter{ transaction.id.bytes contentEquals it.leafData }.size == 1) {
            "The transaction's id should be in the Proof."
        }
        val hashDigestProvider = signatureWithMetadata.getBatchMerkleTreeDigestProvider()
        val batchTreeRoot = signatureWithMetadata.proof!!.calculateRoot(hashDigestProvider)!!
        val signedData = SignableData(batchTreeRoot, signatureWithMetadata.metadata)
        return digitalSignatureVerificationService.verify(
            publicKey = signatureWithMetadata.by,
            signatureSpec = signatureSpec,
            signatureData = signatureWithMetadata.signature.bytes,
            clearData = serializationService.serialize(signedData).bytes
        )
    }

    private fun checkAndGetSignatureSpec(signatureWithMetadata: DigitalSignatureAndMetadata): SignatureSpec {
        val signatureSpec = signatureWithMetadata.metadata.signatureSpec

        val compatibleSpecs = signatureSpecService.compatibleSignatureSpecs(signatureWithMetadata.by)
        require(signatureSpec in compatibleSpecs) {
            "The signature spec in the signature's metadata ('$signatureSpec') is incompatible with its key!"
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
            linkedMapOf(
                "cpiName" to cpiSummary.name,
                "cpiVersion" to cpiSummary.version,
                "cpiSignerSummaryHash" to cpiSummary.signerSummaryHash.toString(),
                BATCH_SIGNATURE_KEY to (batchSettings != emptyMap<String, String>()).toString()
            ) + batchSettings
        )
    }
    private fun TransactionWithMetadata.getDigestSetting(settingKey: String): String {
        return metadata.getDigestSettings()[settingKey]!!
    }
    private val TransactionWithMetadata.notaryMerkleTreeDigestProviderName
        get() = getDigestSetting(NOTARY_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

    private val TransactionWithMetadata.notaryMerkleTreeDigestAlgorithmName
        get() = DigestAlgorithmName(
            getDigestSetting(
                NOTARY_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
            )
        )

    private val TransactionWithMetadata.notaryMerkleTreeDigestOptionsLeafPrefixB64
        get() = getDigestSetting(NOTARY_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY)

    private val TransactionWithMetadata.notaryMerkleTreeDigestOptionsLeafPrefix
        get() = Base64.getDecoder().decode(notaryMerkleTreeDigestOptionsLeafPrefixB64)

    private val TransactionWithMetadata.notaryMerkleTreeDigestOptionsNodePrefixB64
        get() = getDigestSetting(NOTARY_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY)

    private val TransactionWithMetadata.notaryMerkleTreeDigestOptionsNodePrefix
        get() = Base64.getDecoder().decode(notaryMerkleTreeDigestOptionsNodePrefixB64)

    private fun TransactionWithMetadata.getNotaryMerkleTreeDigestProvider(): MerkleTreeHashDigestProvider =
        merkleTreeProvider.createHashDigestProvider(
            notaryMerkleTreeDigestProviderName,
            notaryMerkleTreeDigestAlgorithmName,
            mapOf(
                HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to notaryMerkleTreeDigestOptionsLeafPrefix,
                HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to notaryMerkleTreeDigestOptionsNodePrefix
            )
        )

    private fun getBatchSignatureMetadataSettings(transaction: TransactionWithMetadata): Map<String, String> = mapOf(
        BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to transaction.notaryMerkleTreeDigestProviderName,
        BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to transaction.notaryMerkleTreeDigestAlgorithmName.name,
        BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to transaction.notaryMerkleTreeDigestOptionsLeafPrefixB64,
        BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to transaction.notaryMerkleTreeDigestOptionsNodePrefixB64
    )

    private fun DigitalSignatureAndMetadata.getDigestSetting(settingKey: String): String {
        return metadata.properties[settingKey]!!
    }
    private val DigitalSignatureAndMetadata.batchMerkleTreeDigestProviderName
        get() = getDigestSetting(BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY)

    private val DigitalSignatureAndMetadata.batchMerkleTreeDigestAlgorithmName
        get() = DigestAlgorithmName(
            getDigestSetting(
                BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
            )
        )

    private val DigitalSignatureAndMetadata.batchMerkleTreeDigestOptionsLeafPrefix
        get() = Base64.getDecoder().decode(getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY))

    private val DigitalSignatureAndMetadata.batchMerkleTreeDigestOptionsNodePrefix
        get() = Base64.getDecoder().decode(getDigestSetting(BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY))

    private fun DigitalSignatureAndMetadata.getBatchMerkleTreeDigestProvider(): MerkleTreeHashDigestProvider =
        merkleTreeProvider.createHashDigestProvider(
            batchMerkleTreeDigestProviderName,
            batchMerkleTreeDigestAlgorithmName,
            mapOf(
                HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to batchMerkleTreeDigestOptionsLeafPrefix,
                HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to batchMerkleTreeDigestOptionsNodePrefix
            )
        )

}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummaryImpl(
        name = "CPI name",
        version = "CPI version",
        signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )
