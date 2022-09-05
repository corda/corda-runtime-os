package net.cordacon.example

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordacon.example.utils.createScript
import net.cordacon.example.utils.findStudents
import net.cordacon.example.utils.rollCallName
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id


@InitiatingFlow("roll-call")
class RollCallFlow: RPCStartableFlow {

    private data class SessionAndRecipient(val flowSession: FlowSession, val receipient : MemberX500Name)
    private data class SessionAndResponse(val flowSession: FlowSession, val response : String)

    private companion object {
        private const val RETRIES: Int = 2
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Flow invoked")
        log.info("Initiating roll call")

        val students = findStudents(memberLookup)

        val sessionsAndRecipients = students.map {
            SessionAndRecipient(flowMessaging.initiateFlow(it.name), it.name)
        }

        log.info("Roll call initiated; waiting for responses")
        val responses = sessionsAndRecipients.map {
            val firstResponses = sendRollCall(it)
            val absenteeSessions = firstResponses.filter { r -> r.response.isEmpty() }
                .map { (flowSession) -> flowSession }
            val rechecks = sendRetries(absenteeSessions)
            firstResponses + rechecks
        }.flatten()

        val studentsAndResponses = responses
            .map { Pair(it.flowSession.counterparty, it.response) }
        return createScript(studentsAndResponses, flowEngine.virtualNodeName)
    }

    @Suspendable
    private fun sendRollCall(sessionAndRecipient: SessionAndRecipient) =
        listOf(
            SessionAndResponse(
                sessionAndRecipient.flowSession,
                sessionAndRecipient.flowSession.sendAndReceive(
                    RollCallResponse::class.java,
                    RollCallRequest(sessionAndRecipient.receipient.toString())
                ).unwrap { r -> r }.response
            )
        )

    @Suspendable
    private fun sendRetries(absenteeSessions: List<FlowSession>) =
        absenteeSessions.map { session ->
                val absenceResponses = retryRollCall(session)
                if (absenceResponses.none { (response) -> response.isNotEmpty() }) {
                    persistenceService.persist(AbsenceRecordEntity(name = session.counterparty.rollCallName))
                    absenceResponses.map { SessionAndResponse(session, "") }
                } else {
                    listOf(
                        SessionAndResponse(
                            session, absenceResponses.first { (response) -> response.isNotEmpty() }.response
                        )
                    )
                }
            }.flatten()


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
data class RollCallRequest(val recipientX500: String)
@CordaSerializable
data class RollCallResponse(val response: String)

@CordaSerializable
data class AbsenceResponse(val response: String)

@CordaSerializable
@Entity
data class AbsenceRecordEntity(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    @Column
    val name: String
)
