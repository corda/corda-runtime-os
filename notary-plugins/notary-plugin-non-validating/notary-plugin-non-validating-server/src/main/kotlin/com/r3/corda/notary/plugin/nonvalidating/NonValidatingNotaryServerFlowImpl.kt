package com.r3.corda.notary.plugin.nonvalidating

import com.r3.corda.notary.plugin.common.NotarisationRequestImpl
import com.r3.corda.notary.plugin.common.TransactionParts
import com.r3.corda.notary.plugin.common.toNotarisationResponse
import com.r3.corda.notary.plugin.common.validateRequestSignature
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.core.NotarisationResponse
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger

/**
 * The server-side implementation of the non-validating notary logic.
 * This will be initiated by the client side of this notary plugin: [NonValidatingNotaryClientFlowImpl]
 */
// TODO CORE-7292 What is the best way to define the protocol
@InitiatedBy(protocol = "non-validating-notary")
class NonValidatingNotaryServerFlowImpl : ResponderFlow {

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signatureVerifier: DigitalSignatureVerificationService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    private companion object {
        val logger: Logger = contextLogger()
    }

    /**
     * The main logic is implemented in this function.
     *
     * The logic is very simple in a few steps:
     * 1. Receive and unpack payload from client
     * 2. Run initial validation (signature etc.)
     * 3. Run verification
     * 4. Request uniqueness checking using the [LedgerUniquenessCheckerClientService]
     * 5. Send the [NotarisationResponse] back to the client including the specific
     * [NotaryError][net.corda.v5.ledger.notary.plugin.core.NotaryError] if applicable
     */
    @Suspendable
    override fun call(session: FlowSession) {
        val requestPayload = session.receive(NonValidatingNotarisationPayload::class.java)

        val txParts = validateRequest(session, requestPayload)
        val request = NotarisationRequestImpl(txParts.inputs, txParts.id)

        // TODO This shouldn't ever fail but should add an error handling
        // TODO Discuss this with MGM team but should we able to look up members by X500 name?
        val otherMemberInfo = memberLookup.lookup(session.counterparty)!!
        val otherParty = Party(session.counterparty, otherMemberInfo.sessionInitiationKey)

        validateRequestSignature(
            request,
            otherParty,
            serializationService,
            signatureVerifier,
            requestPayload.requestSignature
        )

        verifyTransaction(requestPayload)

        val uniquenessResponse = clientService.requestUniquenessCheck(
            txParts.id.toString(),
            txParts.inputs.map { it.toString() },
            txParts.references.map { it.toString() },
            txParts.numOutputs,

            // TODO CORE-7251 LedgerTransaction has a non-nullable time window
            //  but the lower bound should be nullable
            txParts.timeWindow.from,
            txParts.timeWindow.until
        )

        logger.debug {
            "Uniqueness check completed for transaction with Tx [${txParts.id}], " +
                    "result is: ${uniquenessResponse.result}"
        }

        session.send(uniquenessResponse.toNotarisationResponse())
    }

    /**
     * This function will validate the request payload received from the notary client.
     *
     * TODO CORE-7249 This function doesn't do much now since we cannot pre-validate anymore, should we remove this?
     */
    @Suppress("TooGenericExceptionCaught")
    private fun validateRequest(otherSideSession: FlowSession,
                                requestPayload: NonValidatingNotarisationPayload): TransactionParts {

        val transactionParts = extractParts(requestPayload)
        logger.debug {
            "Received a notarisation request for Tx [${transactionParts.id}] from [${otherSideSession.counterparty}]"
        }

        return transactionParts
    }

    /**
     * A helper function that constructs an instance of [TransactionParts] from the given transaction.
     *
     * TODO CORE-7249 For now this is basically a dummy function. In the old C5 world this function extracted
     *  the data from either the `NotaryChangeWireTransaction` or the `FilteredTransaction` which
     *  do not exist for now.
     */
    @Suspendable
    private fun extractParts(requestPayload: NonValidatingNotarisationPayload): TransactionParts {
        val signedTx = requestPayload.transaction as UtxoSignedTransaction
        val ledgerTx = signedTx.toLedgerTransaction()

        return TransactionParts(
            signedTx.id,
            requestPayload.numOutputs,
            ledgerTx.timeWindow,
            ledgerTx.inputStateAndRefs.map { it.ref },
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
