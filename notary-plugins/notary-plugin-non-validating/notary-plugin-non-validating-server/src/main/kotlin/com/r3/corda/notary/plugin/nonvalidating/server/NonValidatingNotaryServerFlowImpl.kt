package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarisationRequest
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneralImpl
import com.r3.corda.notary.plugin.common.toNotarisationResponse
import com.r3.corda.notary.plugin.common.validateRequestSignature
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.containsAny
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.time.Instant

/**
 * The server-side implementation of the non-validating notary logic.
 * This will be initiated by the client side of this notary plugin: [NonValidatingNotaryClientFlowImpl]
 */
// TODO CORE-7292 What is the best way to define the protocol
@InitiatedBy(protocol = "non-validating-notary")
class NonValidatingNotaryServerFlowImpl() : ResponderFlow {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signatureVerifier: DigitalSignatureVerificationService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    @CordaInject
    private lateinit var digestService: DigestService

    @CordaInject
    private lateinit var signingService: SigningService

    @CordaInject
    private lateinit var merkleTreeFactory: MerkleTreeFactory

    /**
     * Constructor used for testing to initialize the necessary services
     */
    @VisibleForTesting
    @Suppress("LongParameterList")
    internal constructor(
        clientService: LedgerUniquenessCheckerClientService,
        serializationService: SerializationService,
        signatureVerifier: DigitalSignatureVerificationService,
        memberLookup: MemberLookup,
        merkleTreeFactory: MerkleTreeFactory,
        signingService: SigningService,
        digestService: DigestService
    ) : this() {
        this.clientService = clientService
        this.serializationService = serializationService
        this.signatureVerifier = signatureVerifier
        this.memberLookup = memberLookup
        this.merkleTreeFactory = merkleTreeFactory
        this.signingService = signingService
        this.digestService = digestService
    }

    /**
     * The main logic is implemented in this function.
     *
     * The logic is very simple in a few steps:
     * 1. Receive and unpack payload from client
     * 2. Run initial validation (signature etc.)
     * 3. Run verification
     * 4. Request uniqueness checking using the [LedgerUniquenessCheckerClientService]
     * 5. Send the [NotarisationResponse][com.r3.corda.notary.plugin.common.NotarisationResponse]
     * back to the client including the specific
     * [NotaryError][net.corda.v5.ledger.notary.plugin.core.NotaryError] if applicable
     */
    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val requestPayload = session.receive(NonValidatingNotarisationPayload::class.java)

            val txDetails = validateRequest(requestPayload)

            val request = NotarisationRequest(txDetails.inputs, txDetails.id)

            logger.trace { "Received notarization request for transaction ${request.transactionId}" }

            val otherMemberInfo = memberLookup.lookup(session.counterparty)
                ?: throw IllegalStateException("Could not find counterparty on the network: ${session.counterparty}")

            val otherParty = Party(otherMemberInfo.name, otherMemberInfo.sessionInitiationKey)

            validateRequestSignature(
                request,
                otherParty,
                serializationService,
                signatureVerifier,
                requestPayload.requestSignature
            )

            verifyTransaction(requestPayload)

            logger.trace { "Requesting uniqueness check for transaction ${txDetails.id}" }

            val uniquenessResult = clientService.requestUniquenessCheck(
                txDetails.id.toString(),
                txDetails.inputs.map { it.toString() },
                txDetails.references.map { it.toString() },
                txDetails.numOutputs,
                txDetails.timeWindow.from,
                txDetails.timeWindow.until
            )

            logger.debug {
                "Uniqueness check completed for transaction ${txDetails.id}, result is: ${uniquenessResult}. Sending response " +
                        "to ${session.counterparty}"
            }

            val signature = if (uniquenessResult is UniquenessCheckResultSuccess) {
                signBatch(listOf(txDetails.id), requestPayload.notaryKey).rootSignature
            } else null

            session.send(uniquenessResult.toNotarisationResponse(signature))
        } catch (e: Exception) {
            logger.warn("Error while processing request from client. Cause: $e")
            session.send(
                NotarisationResponse(
                    emptyList(),
                    NotaryErrorGeneralImpl("Error while processing request from client.", e)
                )
            )
        }
    }

    /**
     * This function will validate the request payload received from the notary client.
     *
     * @throws IllegalStateException if the request could not be validated.
     */
    @Suspendable
    @Suppress("TooGenericExceptionCaught")
    private fun validateRequest(requestPayload: NonValidatingNotarisationPayload): NonValidatingNotaryTransactionDetails {
        val transactionParts = try {
            extractParts(requestPayload)
        } catch (e: Exception) {
            logger.warn("Could not validate request. Reason: ${e.message}")
            throw IllegalStateException("Could not validate request.", e)
        }

        // TODO CORE-8976 Add check for notary identity

        return transactionParts
    }

    /**
     * A helper function that constructs an instance of [NonValidatingNotaryTransactionDetails] from the given transaction.
     */
    @Suspendable
    private fun extractParts(requestPayload: NonValidatingNotarisationPayload): NonValidatingNotaryTransactionDetails {
        val filteredTx = requestPayload.transaction as UtxoFilteredTransaction
        // The notary component is not needed by us but we validate that it is present just in case
        requireNotNull(filteredTx.notary) {
            "Notary component could not be found on the transaction"
        }

        requireNotNull(filteredTx.timeWindow) {
            "Time window component could not be found on the transaction"
        }

        val inputStates = filteredTx.inputStateRefs.castOrThrow<UtxoFilteredData.Audit<StateRef>> {
            "Could not fetch input states from the filtered transaction"
        }

        val refStates = filteredTx.referenceStateRefs.castOrThrow<UtxoFilteredData.Audit<StateRef>> {
            "Could not fetch reference states from the filtered transaction"
        }

        val outputStates = filteredTx.outputStateAndRefs.castOrThrow<UtxoFilteredData.SizeOnly<StateAndRef<*>>> {
            "Could not fetch output states from the filtered transaction"
        }

        return NonValidatingNotaryTransactionDetails(
            filteredTx.id,
            outputStates.size,
            filteredTx.timeWindow!!,
            inputStates.values.values.toList(),
            refStates.values.values.toList(),
            filteredTx.notary!!
        )
    }

    /**
     * A non-validating plugin specific verification logic.
     *
     * @throws IllegalStateException if the transaction could not be verified.
     */
    @Suspendable
    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught", "ThrowsCount",)
    private fun verifyTransaction(requestPayload: NonValidatingNotarisationPayload) {
        try {
            (requestPayload.transaction as UtxoFilteredTransaction).verify()
        } catch (e: Exception) {
            logger.warn(
                "Error while validating transaction ${(requestPayload.transaction as UtxoFilteredTransaction).id}, reason: ${e.message}"
            )
            throw IllegalStateException(
                "Error while validating transaction ${(requestPayload.transaction as UtxoFilteredTransaction).id}",
                e
            )
        }
    }

    private inline fun <reified T> Any.castOrThrow(error: () -> String) = this as? T
        ?: throw java.lang.IllegalStateException(error())

    @Suspendable
    private fun signBatch(txIds: List<SecureHash>, notaryServiceKey: PublicKey): BatchSignature {
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

        val sig = signingService.sign(
            merkleTree.root.bytes,
            selectNotaryVNodeSigningKey(notaryServiceKey),
            SignatureSpec.ECDSA_SHA256
        )

        return BatchSignature(
            DigitalSignatureAndMetadata(
                sig,
                DigitalSignatureMetadata(
                    Instant.now(),
                    SignatureSpec.ECDSA_SHA256,
                    mapOf(
                        "platformVersion" to memberLookup.myInfo().platformVersion.toString()
                    )
                )
            ),
            merkleTree
        )
    }

    // TODO CORE-9469 The key selection here will be replaced with the Crypto API once it is finished.
    @Suspendable
    private fun selectNotaryVNodeSigningKey(notaryServiceKey: PublicKey): PublicKey {

        // We select the first key that is not null
        val selectedSigningKey = signingService
            .findMySigningKeys(setOf(notaryServiceKey)).values
            .filterNotNull()
            .firstOrNull()

        // We need to make sure there's at least one key we can sign with
        require(selectedSigningKey != null) {
            "Could not find any keys associated with the notary service's public key."
        }

        // We double check that the selected key is actually part of the notary service key
        require(notaryServiceKey.containsAny(setOf(selectedSigningKey))) {
            "The notary key selected for signing is not associated with the notary service key."
        }

        return selectedSigningKey
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
