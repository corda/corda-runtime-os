package com.r3.corda.notary.plugin.nonvalidating

import com.r3.corda.notary.plugin.common.NotarisationRequestImpl
import com.r3.corda.notary.plugin.common.NotaryException
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.pluggable.NotarisationRequestSignature
import net.corda.v5.ledger.notary.pluggable.NotarisationResponse
import net.corda.v5.ledger.notary.pluggable.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

/**
 * The client that is used for the non-validating notary logic. This class is very simple and uses the basic
 * send-and-receive logic and it will also initiate the server side of the non-validating notary.
 */
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
    private lateinit var hashingService: DigestService

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signingService: SigningService

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java)
    }

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

        val requestSignature = generateRequestSignature()

        val payload = generatePayload(requestSignature)

        val notarisationResponse = session.sendAndReceive(
            NotarisationResponse::class.java,
            payload
        )

        return notarisationResponse.error?.let {
            throw NotaryException(it)
        } ?: notarisationResponse.signatures
    }

    /**
     * This function needs to define how a notarisation payload of type [NonValidatingNotarisationPayload] is generated
     * from the given [requestSignature] parameter.
     */
    private fun generatePayload(requestSignature: NotarisationRequestSignature): NonValidatingNotarisationPayload {
        // TODO CORE-7249 Filtering needed
        return NonValidatingNotarisationPayload(
            stx.toLedgerTransaction(),
            stx.toLedgerTransaction().outputStateAndRefs.size,
            requestSignature
        )
    }

    /**
     * Ensure that transaction ID instances are not referenced in the serialized form in case several input states are outputs of the
     * same transaction.
     */
    @Suppress("ForbiddenComment")
    private fun generateRequestSignature(): NotarisationRequestSignature {
        val notarisationRequest = NotarisationRequestImpl(
            stx.toLedgerTransaction().inputStateAndRefs.map { it.ref },
            stx.id
        )
        return notarisationRequest.generateSignature(memberLookupService.myInfo(), signingService, serializationService)
    }
}
