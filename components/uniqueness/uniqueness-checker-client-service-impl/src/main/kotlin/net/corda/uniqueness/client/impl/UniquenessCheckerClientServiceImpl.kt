package net.corda.uniqueness.client.impl

import net.corda.crypto.merkle.impl.DefaultHashDigestProvider
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.data.uniqueness.*
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.HashingService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.uniqueness.client.UniquenessCheckerClientService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.*
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.serialization.SingletonSerializeAsToken
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
class UniquenessCheckerClientServiceImpl(
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
    @Reference(service = MemberLookup::class)
    private val memberLookup: MemberLookup,
): UniquenessCheckerClientService, SingletonSerializeAsToken {

    private companion object {
        const val THREAD_POOL_SIZE = 5

        val log = contextLogger()
    }

    private val executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    private val myMemberInfo = memberLookup.myInfo()

    // FIXME CORE-6173 this key should be replaced with the notary key
    private val signingLedgerKey = myMemberInfo.ledgerKeys.first()

    override fun commitRequest(
        txId: String,
        inputs: List<String>,
        references: List<String>,
        numberOfOutputStates: Int,
        timeWindowLowerBound: Instant?,
        timeWindowUpperBound: Instant,
    ): Future<UniquenessCheckResponse> {
        log.debug("Received request with id: $txId, sending it to Uniqueness Checker")
        return executorService.submit(Callable {
            processRequest(UniquenessCheckRequest(
                txId,
                inputs,
                references,
                numberOfOutputStates,
                timeWindowLowerBound,
                timeWindowUpperBound
            ))
        })
    }

    // TODO CORE-6243 Batch size is limited to 1 for now
    private fun processRequest(request: UniquenessCheckRequest) =
        uniquenessChecker.processRequests(listOf(request)).first()

    /** Generates a signature over the bach of [txIds]. */
    private fun signBatch(
        txIds: Iterable<SecureHash>
    ): BatchSignature {
        val algorithms = txIds.mapTo(HashSet(), SecureHash::algorithm)
        require(algorithms.size > 0) {
            "Cannot sign an empty batch"
        }
        require(algorithms.size == 1) {
            "Cannot sign a batch with multiple hash algorithms: $algorithms"
        }

        val allLeaves = txIds.map {
            // we don't have a reHash function anymore
            digestService.hash(it.bytes, DigestAlgorithmName(it.algorithm))
        }

        val merkleTree = MerkleTreeFactoryImpl(digestService).createTree(
            allLeaves.map { it.bytes },
            DefaultHashDigestProvider(
                DigestAlgorithmName(algorithms.first()), // TODO will this work?
                digestService
            )
        )

        val merkleTreeRoot = merkleTree.root

        val sig = signingService.sign(
            merkleTreeRoot.bytes,
            signingLedgerKey,
            SignatureSpec(algorithms.first()) // TODO will this work?
        )

        return BatchSignature(
            DigitalSignatureAndMetadata(
                sig,
                DigitalSignatureMetadata(
                    Instant.now(),
                    // TODO how to populate this properly?
                    mapOf("platformVersion" to myMemberInfo.platformVersion.toString())
                )
            ),
            merkleTree
        )
    }
}

data class BatchSignature(
    val rootSignature: DigitalSignatureAndMetadata,
    val fullMerkleTree: MerkleTree) {
    /** Extracts a signature with a partial Merkle tree for the specified leaf in the batch signature. */
    fun forParticipant(txId: SecureHash, hashingService: HashingService) {
        // TODO CORE-6243 how to do this without partial merkle trees?
    }
}