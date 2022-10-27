package com.r3.corda.notary.plugin.nonvalidating

import com.r3.corda.notary.plugin.common.NotarisationRequestImpl
import com.r3.corda.notary.plugin.common.NotaryException
import com.r3.corda.notary.plugin.common.generateRequestSignature
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.core.NotarisationResponse
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * The client that is used for the non-validating notary logic. This class is very simple and uses the basic
 * send-and-receive logic and it will also initiate the server side of the non-validating notary.
 */
// TODO CORE-7292 What is the best way to define the protocol
@InitiatingFlow(protocol = "non-validating-notary")
class NonValidatingNotaryClientFlowImpl(
    private val stx: UtxoSignedTransaction,
    private val notary: Party
) : PluggableNotaryClientFlow {

    @CordaInject
    private lateinit var flowMessaging: FlowMessaging

    @CordaInject
    private lateinit var memberLookupService: MemberLookup

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signingService: SigningService

    /**
     * The main logic of the flow is defined in this function. The execution steps are:
     * 1. Initiating flow with the notary
     * 2. Generating signature for the request payload
     * 3. Creating the payload (using concrete implementation)
     * 4. Sending the request payload to the notary
     * 5. Receiving the response from the notary and returning it or throwing an exception if an error is received
     */
    @Suspendable
    override fun call(): List<DigitalSignatureAndMetadata> {
        val session = flowMessaging.initiateFlow(notary.name)

        val payload = generatePayload(stx)

        val notarisationResponse = session.sendAndReceive(
            NotarisationResponse::class.java,
            payload
        )

        return notarisationResponse.error?.let {
            throw NotaryException(it)
        } ?: notarisationResponse.signatures
    }

    /**
     * This function generates a notarisation request and a signature from that given request via serialization.
     * Then attaches that signature to a [NonValidatingNotarisationPayload].
     */
    @Suspendable
    private fun generatePayload(stx: UtxoSignedTransaction): NonValidatingNotarisationPayload {
        val notarisationRequest = NotarisationRequestImpl(
            stx.toLedgerTransaction().inputStateAndRefs.map { it.ref },
            stx.id
        )

        val requestSignature = generateRequestSignature(
            notarisationRequest,
            memberLookupService.myInfo(),
            serializationService,
            signingService
        )

        // TODO CORE-7249 Filtering needed
        return NonValidatingNotarisationPayload(
            stx.toLedgerTransaction(),
            stx.toLedgerTransaction().outputStateAndRefs.size,
            requestSignature
        )
    }
}
