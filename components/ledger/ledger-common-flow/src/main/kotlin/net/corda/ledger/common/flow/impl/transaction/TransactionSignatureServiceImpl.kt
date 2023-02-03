package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.data.transaction.getNotaryMerkleTreeDigestProvider
import net.corda.ledger.common.data.transaction.notaryMerkleTreeDigestAlgorithmName
import net.corda.ledger.common.data.transaction.notaryMerkleTreeDigestOptionsLeafPrefix
import net.corda.ledger.common.data.transaction.notaryMerkleTreeDigestOptionsLeafPrefixB64
import net.corda.ledger.common.data.transaction.notaryMerkleTreeDigestOptionsNodePrefix
import net.corda.ledger.common.data.transaction.notaryMerkleTreeDigestOptionsNodePrefixB64
import net.corda.ledger.common.data.transaction.notaryMerkleTreeDigestProviderName
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsLeafPrefix
import net.corda.ledger.common.data.transaction.rootMerkleTreeDigestOptionsNodePrefix
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
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
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

const val BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY = "batchMerkleTreeDigestProviderName"
const val BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY = "batchMerkleTreeDigestAlgorithmName"
const val BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsLeafPrefixB64"
const val BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY = "batchMerkleTreeDigestOptionsNodePrefixB64"

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
        val availableKeys = getAvailableKeysFor(publicKeys)
        return availableKeys.map { publicKey ->
            val signatureSpec = requireNotNull(signatureSpecService.defaultSignatureSpec(publicKey)) {
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
        confirmBatchSigningRequirements(transactions)

        val availableKeys = getAvailableKeysFor(publicKeys)

        val hashDigestProvider = transactions.first().getNotaryMerkleTreeDigestProvider(merkleTreeProvider)
        val batchTree = merkleTreeProvider.createTree(transactions.map { it.id.bytes }, hashDigestProvider)

        val batchSignaturesWithMeta = availableKeys.map { publicKey ->
            val signatureSpec = requireNotNull(signatureSpecService.defaultSignatureSpec(publicKey)) {
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
        return List(transactions.size) {
            val proof = batchTree.createAuditProof(listOf(it))
            batchSignaturesWithMeta.map { (signature, signatureMetadata) ->
                DigitalSignatureAndMetadata(signature, signatureMetadata, proof)
            }
        }
    }

    override fun verifySignature(transaction: TransactionWithMetadata, signatureWithMetadata: DigitalSignatureAndMetadata) {
        val signatureSpec = checkAndGetSignatureSpec(signatureWithMetadata)

        val proof = signatureWithMetadata.proof
        val signedHash = if (proof == null) {   // Simple signature
            transaction.id
        } else {                                // Batch signature

            confirmSignatureBatchMerkleSettingsMatchWithTransactions(transaction, signatureWithMetadata)
            confirmHashPrefixesAreDifferent(listOf(transaction))

            require(proof.leaves.filter { transaction.id.bytes contentEquals it.leafData }.size == 1) {
                "The transaction's id should be proven by the proof."
            }
            val hashDigestProvider = signatureWithMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider)
            val batchTreeRoot = requireNotNull(proof.calculateRoot(hashDigestProvider)) {
                "The proof's root hash cannot be calculated."
            }
            batchTreeRoot
        }

        val signedData = SignableData(signedHash, signatureWithMetadata.metadata)

        return digitalSignatureVerificationService.verify(
            publicKey = signatureWithMetadata.by,
            signatureSpec = signatureSpec,
            signatureData = signatureWithMetadata.signature.bytes,
            clearData = serializationService.serialize(signedData).bytes
        )
    }

    private fun confirmBatchSigningRequirements(transactions: List<TransactionWithMetadata>) {
        require(transactions.isNotEmpty()) { "Cannot sign empty batch." }
        require(transactions.all { it.notaryMerkleTreeDigestProviderName == HASH_DIGEST_PROVIDER_TWEAKABLE_NAME }) {
            "Batch signature supports only $HASH_DIGEST_PROVIDER_TWEAKABLE_NAME."
        }
        require(transactions.map { it.notaryMerkleTreeDigestAlgorithmName }.distinct().size == 1) {
            "Notary merkle tree digest algorithm names should be the same in a batch to be signed."
        }
        require(transactions.map { it.notaryMerkleTreeDigestOptionsLeafPrefix }.distinct().size == 1) {
            "Notary merkle tree digest leaf prefixes should be the same in a batch to be signed."
        }
        require(transactions.map { it.notaryMerkleTreeDigestOptionsNodePrefix }.distinct().size == 1) {
            "Notary merkle tree digest node prefixes should be the same in a batch to be signed."
        }
        require(transactions.none {
            it.notaryMerkleTreeDigestOptionsLeafPrefix contentEquals it.notaryMerkleTreeDigestOptionsNodePrefix
        }) {
            "Notary merkle tree digest node prefixes and leaf prefixes need to be different for each transactions."
        }
        confirmHashPrefixesAreDifferent(transactions)
    }

    private fun confirmHashPrefixesAreDifferent(transactions: List<TransactionWithMetadata>) {
        require(transactions.none { it.notaryMerkleTreeDigestOptionsLeafPrefix contentEquals it.rootMerkleTreeDigestOptionsLeafPrefix }) {
            "The transaction can be batch signed only if its root and the notary leaf prefixes are different."
        }
        require(transactions.none { it.notaryMerkleTreeDigestOptionsNodePrefix contentEquals it.rootMerkleTreeDigestOptionsNodePrefix }) {
            "The transaction can be batch signed only if its root and the notary node prefixes are different."
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
        require(transaction.notaryMerkleTreeDigestProviderName == signatureWithMetadata.batchMerkleTreeDigestProviderName) {
            "Batch signature's digest provider should match with the transaction's notary merkle digest provider."
        }
        require(transaction.notaryMerkleTreeDigestAlgorithmName == signatureWithMetadata.batchMerkleTreeDigestAlgorithmName) {
            "Batch signature's algorithm should match with the transaction's notary merkle algorithm name."
        }
        require(
            transaction.notaryMerkleTreeDigestOptionsLeafPrefix contentEquals
                    signatureWithMetadata.batchMerkleTreeDigestOptionsLeafPrefix
        ) {
            "Batch signature's leaf prefix should match with the transaction's notary merkle leaf prefix."
        }
        require(
            transaction.notaryMerkleTreeDigestOptionsNodePrefix contentEquals
                    signatureWithMetadata.batchMerkleTreeDigestOptionsNodePrefix
        ) {
            "Batch signature's node prefix should match with the transaction's notary merkle node prefix."
        }
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
                "cpiSignerSummaryHash" to cpiSummary.signerSummaryHash.toString()
            ) + batchSettings
        )
    }

    private fun getBatchSignatureMetadataSettings(transaction: TransactionWithMetadata): Map<String, String> = mapOf(
        BATCH_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY to transaction.notaryMerkleTreeDigestProviderName,
        BATCH_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to transaction.notaryMerkleTreeDigestAlgorithmName.name,
        BATCH_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to transaction.notaryMerkleTreeDigestOptionsLeafPrefixB64,
        BATCH_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to transaction.notaryMerkleTreeDigestOptionsNodePrefixB64
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
