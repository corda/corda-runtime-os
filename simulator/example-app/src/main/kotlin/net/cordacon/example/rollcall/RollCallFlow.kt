package net.cordacon.example.rollcall

import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.cordacon.example.rollcall.utils.BaseScriptMaker
import net.cordacon.example.rollcall.utils.ScriptMaker
import net.cordacon.example.rollcall.utils.findStudents
import org.slf4j.LoggerFactory


@InitiatingFlow(protocol = "roll-call")
class RollCallFlow(val scriptMaker: ScriptMaker = BaseScriptMaker()): ClientStartableFlow {

    private data class SessionAndRecipient(val flowSession: FlowSession, val receipient : MemberX500Name)
    private data class SessionAndResponse(val flowSession: FlowSession, val response : String)

    private companion object {
        private const val RETRIES: Int = 2
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var signingService: SigningService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Initiating roll call")

        val students = findStudents(memberLookup)

        val truancyOffice = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            RollCallInitiationRequest::class.java
        ).truancyOfficeX500

        val sessionsAndRecipients = students.map {
            SessionAndRecipient(flowMessaging.initiateFlow(it.name), it.name)
        }

        log.info("Roll call initiated; waiting for responses")
        val responses = sessionsAndRecipients.map {
            val firstResponse = sendRollCall(it)
            val rechecks = if (firstResponse.response.isEmpty()) {
                sendRetries(firstResponse.flowSession)
            } else {
                listOf()
            }
            sendTruancyRecord(truancyOffice, getTruantsFromRetryResults(rechecks))
            listOf(firstResponse) + rechecks
        }.flatten()

        val studentsAndResponses = responses
            .map { Pair(it.flowSession.counterparty, it.response) }

        return scriptMaker.createScript(studentsAndResponses, flowEngine.virtualNodeName)
    }

    private fun getTruantsFromRetryResults(rechecks: List<SessionAndResponse>): List<MemberX500Name> {
        val candidateTruants = rechecks.map { it.flowSession.counterparty }.toSet()
        val truants = candidateTruants.filter { candidate ->
            rechecks.none {
                    allpairs -> allpairs.flowSession.counterparty == candidate && allpairs.response.isNotEmpty()
            }
        }
        return truants
    }

    @Suspendable
    private fun sendTruancyRecord(truancyOffice : MemberX500Name, truants: List<MemberX500Name>) {
        if (truants.isNotEmpty()) {
            val unsignedTruants = serializationService.serialize(truants)
            val signedTruants = signingService.sign(
                unsignedTruants.bytes,
                memberLookup.myInfo().ledgerKeys[0],
                SignatureSpec.ECDSA_SHA256
            )
            val truancySubFlow = TruancySubFlow(truancyOffice, TruancyRecord(truants, signedTruants))
            flowEngine.subFlow(truancySubFlow)
        }
    }

    @Suspendable
    private fun sendRollCall(sessionAndRecipient: SessionAndRecipient) =
        SessionAndResponse(
            sessionAndRecipient.flowSession,
            sessionAndRecipient.flowSession.sendAndReceive(
                RollCallResponse::class.java,
                RollCallRequest(sessionAndRecipient.receipient)
            ).response
        )

    @Suspendable
    private fun sendRetries(session: FlowSession): List<SessionAndResponse> {
        val absenceResponses = retryRollCall(session)
        if (absenceResponses.none { (response) -> response.isNotEmpty() }) {
            return absenceResponses.map { SessionAndResponse(session, "") }
        } else {
            return listOf(
                SessionAndResponse(
                    session, absenceResponses.first { (response) -> response.isNotEmpty() }.response
                )
            )
        }
    }


    @Suspendable
    private fun retryRollCall(session: FlowSession): List<AbsenceResponse> {
        var retries = 0
        val responses = mutableListOf<AbsenceResponse>()
        while(retries < RETRIES && responses.none { it.response.isNotEmpty() }) {
            responses.add(AbsenceResponse(flowEngine.subFlow(AbsenceSubFlow(session.counterparty))))
            retries++
        }
        return responses
    }
}
@CordaSerializable
data class RollCallInitiationRequest(val truancyOfficeX500: MemberX500Name)

@CordaSerializable
data class RollCallRequest(val recipientX500: MemberX500Name)
@CordaSerializable
data class RollCallResponse(val response: String)

@CordaSerializable
data class AbsenceResponse(val response: String)

@CordaSerializable
data class TruancyRecord(val absentees: List<MemberX500Name>, val signature: DigitalSignature.WithKey)
