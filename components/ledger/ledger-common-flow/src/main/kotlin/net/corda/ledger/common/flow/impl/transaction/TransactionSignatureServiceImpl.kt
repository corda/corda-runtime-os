package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignableData
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
        val availableKeys = getAvailableKeysFor(publicKeys)
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
        val hashDigestProvider = transactions.first().getNotaryMerkleTreeDigestProvider(merkleTreeProvider)

        val batchTree = merkleTreeProvider.createTree(transactions.map { it.id.bytes }, hashDigestProvider)

        val availableKeys = getAvailableKeysFor(publicKeys)

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

        val proof = requireNotNull(signatureWithMetadata.proof) {
            "Batch signature should have a Merkle Proof attached."
        }

        require(transaction.notaryMerkleTreeDigestProviderName == signatureWithMetadata.batchMerkleTreeDigestProviderName ) {
            "Batch signature's digest provider should match with the transaction's notary merkle digest provider."
        }
        require(transaction.notaryMerkleTreeDigestAlgorithmName == signatureWithMetadata.batchMerkleTreeDigestAlgorithmName) {
            "Batch signature's algorithm should match with the transaction's notary merkle algorithm name."
        }
        require(transaction.notaryMerkleTreeDigestOptionsLeafPrefix contentEquals
                    signatureWithMetadata.batchMerkleTreeDigestOptionsLeafPrefix
        ) {
            "Batch signature's leaf prefix should match with the transaction's notary merkle leaf prefix."
        }
        require(transaction.notaryMerkleTreeDigestOptionsNodePrefix contentEquals
                    signatureWithMetadata.batchMerkleTreeDigestOptionsNodePrefix
        ) {
            "Batch signature's node prefix should match with the transaction's notary merkle node prefix."
        }

        require(proof.leaves.filter{ transaction.id.bytes contentEquals it.leafData }.size == 1) {
            "The transaction's id should be proven by the proof."
        }
        val hashDigestProvider = signatureWithMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider)
        val batchTreeRoot = requireNotNull(proof.calculateRoot(hashDigestProvider)) {
            "The proof's root hash cannot be calculated."
        }
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
