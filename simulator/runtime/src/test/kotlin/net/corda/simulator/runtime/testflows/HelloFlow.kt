package net.corda.simulator.runtime.testflows

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.utxo.UtxoLedgerService

@InitiatingFlow(protocol = "hello")
class HelloFlow : ClientStartableFlow {

    @CordaInject
    lateinit var signatureSpecService: SignatureSpecService

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var signingService: SigningService

    @CordaInject
    lateinit var signatureVerificationService: DigitalSignatureVerificationService

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val name = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java).name
        val greeting = flowEngine.subFlow(object : SubFlow<String> {
            @Suspendable
            override fun call(): String = "Hello"
        })
        return "$greeting $name!"
    }
}

data class InputMessage(val name : String)