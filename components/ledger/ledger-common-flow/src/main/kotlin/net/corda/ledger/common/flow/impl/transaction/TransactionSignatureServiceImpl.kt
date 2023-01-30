package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
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
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey
import java.time.Instant

@Suppress("Unused", "LongParameterList")
@Component(
    service = [TransactionSignatureService::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE,
    property = [ CORDA_SYSTEM_SERVICE ]
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
        return signingService.findMySigningKeys(publicKeys.toSet()).values.filterNotNull().map { publicKey ->
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

    override fun verifySignature(transactionId: SecureHash, signatureWithMetadata: DigitalSignatureAndMetadata) {
        val signatureSpec = checkAndGetSignatureSpec(signatureWithMetadata)

        val signedData = SignableData(transactionId, signatureWithMetadata.metadata)
        digitalSignatureVerificationService.verify(
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

    override fun verifyNotarySignature(transactionId: SecureHash, signatureWithMetadata: DigitalSignatureAndMetadata) {
        val txIds = listOf(transactionId)
        // Copy from net.corda.uniqueness.client.impl.LedgerUniquenessCheckerClientServiceImpl.signBatch
        // TODO CORE-8954 transition to proper verify
        val algorithms = txIds.mapTo(HashSet(), SecureHash::algorithm)
        require(algorithms.size > 0) {
            "Cannot sign an empty batch"
        }
        require(algorithms.size == 1) {
            "Cannot sign a batch with multiple hash algorithms: $algorithms"
        }

        val algorithm = algorithms.first()

        val allLeaves = txIds.map {
            // we don't have a reHash function anymore
            digestService.hash(it.bytes, DigestAlgorithmName(algorithm))
        }

        val hashDigest = merkleTreeProvider.createHashDigestProvider(
            HASH_DIGEST_PROVIDER_DEFAULT_NAME,
            DigestAlgorithmName(algorithm)
        )

        val merkleTree = merkleTreeProvider.createTree(
            allLeaves.map { it.bytes },
            hashDigest
        )
        // End of Copy

        val signatureSpec = checkAndGetSignatureSpec(signatureWithMetadata)

        digitalSignatureVerificationService.verify(
            publicKey = signatureWithMetadata.by,
            signatureSpec = signatureSpec,
            signatureData = signatureWithMetadata.signature.bytes,
            clearData = merkleTree.root.bytes
        )
    }

    private fun getSignatureMetadata(signatureSpec: SignatureSpec): DigitalSignatureMetadata {
        val cpiSummary = getCpiSummary()
        return DigitalSignatureMetadata(
            Instant.now(),
            signatureSpec,
            linkedMapOf(
                "cpiName" to cpiSummary.name,
                "cpiVersion" to cpiSummary.version,
                "cpiSignerSummaryHash" to cpiSummary.signerSummaryHash.toString()
            )
        )
    }
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