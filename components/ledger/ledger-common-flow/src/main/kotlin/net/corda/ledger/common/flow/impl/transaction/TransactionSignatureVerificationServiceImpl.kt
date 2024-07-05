package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.bytes
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Suppress("Unused", "LongParameterList")
@Component(
    service = [
        TransactionSignatureVerificationService::class,
        TransactionSignatureVerificationServiceInternal::class,
        UsedByFlow::class,
        UsedByVerification::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class TransactionSignatureVerificationServiceImpl @Activate constructor(

    // Need to stub
    @Reference(service = SerializationServiceInternal::class)
    private val serializationService: SerializationServiceInternal,

    // Can instantiate
    @Reference(service = DigitalSignatureVerificationService::class)
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,

    // Can instantiate
    @Reference(service = SignatureSpecService::class)
    private val signatureSpecService: SignatureSpecService,

    // Can instantiate
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,

    // Can instantiate
    @Reference(service = DigestService::class)
    private val digestService: DigestService,

    // Can instantiate
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : TransactionSignatureVerificationService,
    TransactionSignatureVerificationServiceInternal,
    SingletonSerializeAsToken,
    UsedByFlow,
    UsedByVerification {

    override fun verifySignature(
        transaction: TransactionWithMetadata,
        signatureWithMetadata: DigitalSignatureAndMetadata,
        publicKey: PublicKey
    ) {
        if (signatureWithMetadata.proof != null) {
            transaction.confirmBatchMerkleSettingsMatch(signatureWithMetadata)
            listOf(transaction).confirmHashPrefixesAreDifferent()
        }
        verifySignature(transaction.id, signatureWithMetadata, publicKey)
    }

    override fun verifySignature(
        secureHash: SecureHash,
        signatureWithMetadata: DigitalSignatureAndMetadata,
        publicKey: PublicKey
    ) {
        val digestAlgorithmOfKeyId = signatureWithMetadata.signature.by.algorithm
        val publicKeyId = getIdOfPublicKey(publicKey, digestAlgorithmOfKeyId)
        require(publicKeyId == signatureWithMetadata.signature.by) {
            "The key Id of the provided signature does not match the provided public key's id."
        }
        val signatureSpec =
            checkSignatureSpec(
                signatureWithMetadata.metadata.signatureSpec,
                publicKey
            )

        val proof = signatureWithMetadata.proof
        val signedHash = if (proof == null) { // Simple signature
            secureHash
        } else { // Batch signature
            require(proof.leaves.filter { secureHash.bytes contentEquals it.leafData }.size == 1) {
                "The transaction id cannot be found in the provided Merkle proof for the batch signature" +
                    " - the signature cannot be verified."
            }
            val hashDigestProvider = signatureWithMetadata.getBatchMerkleTreeDigestProvider(merkleTreeProvider)
            proof.calculateRoot(hashDigestProvider)
        }

        val signedData = SignableData(signedHash, signatureWithMetadata.metadata)

        return digitalSignatureVerificationService.verify(
            serializationService.serialize(signedData, withCompression = false).bytes,
            signatureWithMetadata.signature.bytes,
            publicKey,
            signatureSpec
        )
    }

    override fun getIdOfPublicKey(publicKey: PublicKey, digestAlgorithmName: String): SecureHash {
        return digestService.hash(
            keyEncodingService.encodeAsByteArray(publicKey),
            DigestAlgorithmName(digestAlgorithmName)
        )
    }

    private fun checkSignatureSpec(signatureSpec: SignatureSpec, signingKey: PublicKey): SignatureSpec {
        val compatibleSpecs = signatureSpecService.compatibleSignatureSpecs(signingKey)
        require(signatureSpec in compatibleSpecs) {
            "The signature spec in the signature metadata ('$signatureSpec') is incompatible with its key!"
        }
        return signatureSpec
    }
}
