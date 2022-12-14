package net.corda.uniqueness.client.impl

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandbox.type.UsedByFlow
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResponseImpl
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.uniqueness.model.UniquenessCheckResponse
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.time.Instant

/**
 * Implementation of the Uniqueness Checker Client Service which will invoke the batched uniqueness checker
 * through the message bus. This communication uses the external events API. Once the uniqueness checker has
 * finished the validation of the given batch it will return the response to the client service.
 */
@Component(service = [ LedgerUniquenessCheckerClientService::class, UsedByFlow::class ], scope = PROTOTYPE)
class LedgerUniquenessCheckerClientServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = MerkleTreeFactory::class)
    private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = MemberLookup::class)
    private val memberLookup: MemberLookup
): LedgerUniquenessCheckerClientService, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun requestUniquenessCheck(
        txId: String,
        inputStates: List<String>,
        referenceStates: List<String>,
        numOutputStates: Int,
        timeWindowLowerBound: Instant?,
        timeWindowUpperBound: Instant
    ): UniquenessCheckResponse {
        log.info("Received request with id: $txId, sending it to Uniqueness Checker")

        val result = externalEventExecutor.execute(
            UniquenessCheckExternalEventFactory::class.java,
            UniquenessCheckExternalEventParams(
                txId,
                inputStates,
                referenceStates,
                numOutputStates,
                timeWindowLowerBound,
                timeWindowUpperBound
            )
        )

        val signature = if (result is UniquenessCheckResultSuccess) {
            signBatch(listOf(SecureHash.parse(txId))).rootSignature
        } else null

        return UniquenessCheckResponseImpl(
            result,
            signature
        )
    }

    @Suspendable
    private fun signBatch(txIds: List<SecureHash>): BatchSignature {
        // TODO CORE-6615 This validation mechanism needs to be
        //  reconsidered in the future
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

        val hashDigest = merkleTreeFactory.createHashDigest(
            HASH_DIGEST_PROVIDER_DEFAULT_NAME,
            DigestAlgorithmName(algorithm)
        )

        val merkleTree = merkleTreeFactory.createTree(
            allLeaves.map { it.bytes },
            hashDigest
        )

        val myInfo = memberLookup.myInfo()

        // FIXME CORE-6173 this key should be replaced with the notary key
        val signingLedgerKey = myInfo.ledgerKeys.first()

        val sig = signingService.sign(
            merkleTree.root.bytes,
            signingLedgerKey,
            SignatureSpec.ECDSA_SHA256
        )

        return BatchSignature(
            DigitalSignatureAndMetadata(
                sig,
                DigitalSignatureMetadata(
                    Instant.now(),
                    mapOf(
                        "platformVersion" to myInfo.platformVersion.toString(),
                        "signatureSpec" to SignatureSpec.ECDSA_SHA256.signatureName
                    )
                )
            ),
            merkleTree
        )
    }
}

data class BatchSignature(
    val rootSignature: DigitalSignatureAndMetadata,
    val fullMerkleTree: MerkleTree
) {

    /** Extracts a signature with a partial Merkle tree for the specified leaf in the batch signature. */
    // TODO CORE-6243 how to do this without partial merkle trees?
    /*fun forParticipant(txId: SecureHash, hashingService: HashingService) { }*/
}
