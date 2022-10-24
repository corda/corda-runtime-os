package com.r3.corda.notary.plugin.nonvalidating

import com.r3.corda.notary.plugin.common.NotarisationRequestImpl
import com.r3.corda.notary.plugin.common.TransactionParts
import com.r3.corda.notary.plugin.common.sendUniquenessServiceCommitStatus
import com.r3.corda.notary.plugin.common.validateRequestSignature
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The server-side implementation of the non-validating notary logic.
 * This will be initiated by the client side of this notary plugin: [NonValidatingNotaryClientFlowImpl]
 */
@InitiatedBy(protocol = "non-validating-notary")
class NonValidatingNotaryServerFlowImpl : ResponderFlow {

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signatureVerifier: DigitalSignatureVerificationService

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    private var transactionId: SecureHash? = null

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * The main logic is implemented in this function.
     *
     * The logic is very simple in a few steps:
     * 1. Receive and unpack payload from client
     * 2. Run initial validation (signature etc.)
     * 3. Run verification
     * 4. Commit to the database using the [LedgerUniquenessCheckerClientService]
     * 5. Send the [net.corda.v5.application.uniqueness.model.UniquenessCheckResponse] back to the client
     */
    @Suspendable
    override fun call(session: FlowSession) {
        val requestPayload = receivePayload(session)

        val tx: TransactionParts = validateRequest(session, requestPayload)
        val request = NotarisationRequestImpl(tx.inputs, tx.id)

        // TODO This shouldn't ever fail but should add an error handling
        // TODO Discuss this with MGM team but should we able to look up members by X500 name?
        val otherMemberInfo = memberLookup.lookup(session.counterparty)!!
        val otherParty = Party(session.counterparty, otherMemberInfo.sessionInitiationKey)

        validateRequestSignature(
            otherParty,
            serializationService,
            signatureVerifier,
            request,
            requestPayload.requestSignature
        )

        verifyTransaction(requestPayload)

        val uniquenessResponse = clientService.requestUniquenessCheck(
            tx.id.toString(),
            tx.inputs.map { it.toString() },
            tx.references.map { it.toString() },
            tx.numOutputs,

            // TODO CORE-7251 LedgerTransaction has a non-nullable time window but the lower bound should be nullable
            tx.timeWindow.from,
            tx.timeWindow.until
        )

        sendUniquenessServiceCommitStatus(logger, session, transactionId, uniquenessResponse)
    }

    /**
     * This function defines how the payload should be received from the client.
     */
    @Suspendable
    private fun receivePayload(otherSideSession: FlowSession): NonValidatingNotarisationPayload {
        return otherSideSession.receive(NonValidatingNotarisationPayload::class.java)
    }

    /**
     * This function will validate the request payload received from the notary client.
     *
     * TODO CORE-7249 This function doesn't do much now since we cannot pre-validate anymore, should we remove this?
     */
    @Suppress("TooGenericExceptionCaught")
    private fun validateRequest(otherSideSession: FlowSession,
                                requestPayload: NonValidatingNotarisationPayload): TransactionParts {

        val transaction = extractParts(requestPayload)
        transactionId = transaction.id
        logger.info("Received a notarisation request for Tx [$transactionId] from [${otherSideSession.counterparty}]")
        logger.debug { "Tx [$transactionId] contains ${transaction.numOutputs} output states" }

        return transaction
    }

    /**
     * A helper function that constructs an instance of [TransactionParts] from the given transaction.
     *
     * TODO CORE-7249 For now this is basically a dummy function. In the old C5 world this function extracted
     *  the data from either the `NotaryChangeWireTransaction` or the `FilteredTransaction` which
     *  do not exist for now.
     */
    private fun extractParts(requestPayload: NonValidatingNotarisationPayload): TransactionParts {
        val signedTx = requestPayload.transaction as UtxoSignedTransaction
        val ledgerTx = signedTx.toLedgerTransaction()

        return TransactionParts(
            signedTx.id,
            ledgerTx.inputStateAndRefs.map { it.ref },
            requestPayload.numOutputs,
            ledgerTx.timeWindow,
            ledgerTx.referenceInputStateAndRefs.map { it.ref }
        )
    }

    /**
     * A non-validating plugin specific verification logic.
     *
     * TODO CORE-7249 This function is not doing anything for now, as FilteredTransaction doesn't exist
     *  and that's the only verification logic we need in the plugin server.
     */
    @Suspendable
    @Suppress(
        "NestedBlockDepth",
        "TooGenericExceptionCaught",
        "ThrowsCount",
        "Unused_Parameter" // TODO CORE-7249 Remove once this function is actually utilised
    )
    private fun verifyTransaction(requestPayload: NonValidatingNotarisationPayload) {}
}
