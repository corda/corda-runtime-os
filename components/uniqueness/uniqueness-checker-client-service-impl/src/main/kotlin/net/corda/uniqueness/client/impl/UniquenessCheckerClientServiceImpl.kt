package net.corda.uniqueness.client.impl

import net.corda.data.uniqueness.UniquenessCheckExternalRequest
import net.corda.data.uniqueness.UniquenessCheckExternalResultSuccess
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.uniqueness.client.UniquenessCheckerClientService
import net.corda.v5.application.uniqueness.model.UniquenessCheckResponse
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.time.Instant
import java.util.*
import java.util.concurrent.*

/**
 * TODO Add more specific KDocs once CORE-4730 is finished
 */
@Component(service = [ UniquenessCheckerClientService::class, SingletonSerializeAsToken::class ], scope = ServiceScope.PROTOTYPE)
class UniquenessCheckerClientServiceImpl @Activate constructor(
    // TODO for now uniqueness checker is referenced,
    //  but once CORE-4730 is finished it will be invoked
    //  through the message bus. This will refer to the
    //  "fake" uniqueness checker for now
    @Reference(service = UniquenessChecker::class)
    private val uniquenessChecker: UniquenessChecker,

    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = MerkleTreeFactory::class)
    private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = MemberLookup::class)
    private val memberLookup: MemberLookup,
): UniquenessCheckerClientService, SingletonSerializeAsToken {

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
    ): Future<UniquenessCheckResponse> {
        log.info("Received request with id: $txId, sending it to Uniqueness Checker")

        val request = UniquenessCheckExternalRequest(
            txId,
            inputStates,
            referenceStates,
            numOutputStates,
            timeWindowLowerBound,
            timeWindowUpperBound
        )

        val txIds = listOf(SecureHash.create(request.txId))

        val response = uniquenessChecker.processRequests(listOf(request)).first()

        val result = if (response.result is UniquenessCheckExternalResultSuccess) {
            UniquenessCheckResponse(
                UniquenessCheckResult.Success(
                    Instant.now()
                ),
                signBatch(txIds).rootSignature
            )
        } else {
            UniquenessCheckResponse(
                UniquenessCheckResult.fromExternalError(response),
                null
            )
        }

        // TODO For now this is not an actual async call, once we start interacting
        //  with the message bus this will become an actual async call
        return CompletableFuture.completedFuture(result)
    }

    @Suspendable
    private fun signBatch(txIds: List<SecureHash>): BatchSignature {
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

        val hashDigestProvider = merkleTreeFactory.createHashDigestProvider(
            HASH_DIGEST_PROVIDER_DEFAULT_NAME,
            DigestAlgorithmName(algorithm)
        )

        val merkleTree = merkleTreeFactory.createTree(
            allLeaves.map { it.bytes },
            hashDigestProvider
        )

        val merkleTreeRoot = merkleTree.root

        val myInfo = memberLookup.myInfo()

        // FIXME CORE-6173 this key should be replaced with the notary key
        val signingLedgerKey = myInfo.ledgerKeys.first()

        val sig = signingService.sign(
            merkleTreeRoot.bytes,
            signingLedgerKey,
            SignatureSpec.ECDSA_SHA256
        )

        return BatchSignature(
            DigitalSignatureAndMetadata(
                sig,
                DigitalSignatureMetadata(
                    Instant.now(),
                    // TODO how to populate this properly?
                    mapOf("platformVersion" to myInfo.platformVersion.toString())
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