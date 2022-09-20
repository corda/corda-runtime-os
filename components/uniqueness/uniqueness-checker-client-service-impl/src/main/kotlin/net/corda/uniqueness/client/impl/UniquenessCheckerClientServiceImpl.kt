package net.corda.uniqueness.client.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.identity.HoldingIdentity
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultSuccessAvro
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.datamodel.common.toUniquenessResult
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResponseImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.uniqueness.model.UniquenessCheckResponse
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.time.Instant
import java.util.*

/**
 * TODO Add more specific KDocs once CORE-4730 is finished
 */
@Component(service = [ LedgerUniquenessCheckerClientService::class, SingletonSerializeAsToken::class ], scope = ServiceScope.PROTOTYPE)
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
): LedgerUniquenessCheckerClientService, SingletonSerializeAsToken {

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

        @Suppress("ForbiddenComment")
        // TODO: CORE-4730 to pass through the Vnode holding id plus a sensible event context
        val request = UniquenessCheckRequestAvro(
            HoldingIdentity("DUMMY_X500_NAME", "DUMMY_GROUP_ID"),
            ExternalEventContext(
                UUID.randomUUID().toString(),
                "DUMMY_FLOW_ID",
                KeyValuePairList(emptyList())),
            txId,
            inputStates,
            referenceStates,
            numOutputStates,
            timeWindowLowerBound,
            timeWindowUpperBound
        )

        val txIds = listOf(SecureHash.parse(request.txId))

        val uniquenessCheckResponse = uniquenessChecker.processRequests(listOf(request)).first()

        val result = uniquenessCheckResponse.toUniquenessResult()
        val signature = if (uniquenessCheckResponse.result is UniquenessCheckResultSuccessAvro) {
            signBatch(txIds).rootSignature
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

        val hashDigestProvider = merkleTreeFactory.createHashDigestProvider(
            HASH_DIGEST_PROVIDER_DEFAULT_NAME,
            DigestAlgorithmName(algorithm)
        )

        val merkleTree = merkleTreeFactory.createTree(
            allLeaves.map { it.bytes },
            hashDigestProvider
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
