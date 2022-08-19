package net.cordacon.example

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

val MemberX500Name.rollCallName: String
    get() {
        return this.commonName ?: this.organisation
    }

@InitiatingFlow("roll-call")
class RollCallFlow: RPCStartableFlow {

    private companion object {
        private const val RETRIES: Int = 2
        private val nl = System.lineSeparator()
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

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Flow invoked")

        val rollCall = requestBody.getRequestBodyAs(jsonMarshallingService, RollCallInitiationRequest::class.java)


        log.info("Initiating roll call")
        val sessionsAndRecipients = rollCall.recipientsX500.map {
            Pair(flowMessaging.initiateFlow(MemberX500Name.parse(it)), it)
        }

        log.info("Roll call initiated; waiting for responses")
        val teacherPrompt = flowEngine.virtualNodeName.rollCallName.uppercase()
        val responses = sessionsAndRecipients.map {
            val firstResponses = listOf(Pair(
                it.first, it.first.sendAndReceive(
                    RollCallResponse::class.java,
                    RollCallRequest(it.second)
                ).unwrap { r -> r }.response
            ))
            val rechecks = firstResponses.filter { r -> r.second.isEmpty() }
                .map { r ->
                    val absenceResponses = retryRollCall(r)
                    if (absenceResponses.none{ar -> ar.response.isNotEmpty()}) {
                        persistenceService.persist(AbsenceRecordEntity(name = r.first.counterparty.rollCallName))
                        absenceResponses.map { _ -> Pair(r.first, "") }
                    } else {
                        listOf(
                            Pair(r.first, absenceResponses.first { ar -> ar.response.isNotEmpty() }.response))
                    }
                }.flatten()
            firstResponses + rechecks
        }.flatten()

        return responses.joinToString("") {
            val student = it.first.counterparty.rollCallName
            val teacherAsking = "$teacherPrompt: $student?$nl"
            val studentResponding =
                if (it.second.isNotEmpty()) {
                    student.uppercase() + ": " + it.second + nl
                } else {
                    ""
                }
            teacherAsking + studentResponding
        }
    }

    @Suspendable
    private fun retryRollCall(
        r: Pair<FlowSession, String>
    ) : List<AbsenceResponse> {
        var retries = 0
        val responses = mutableListOf<AbsenceResponse>()
        while(retries < RETRIES && responses.none { it.response.isNotEmpty() }) {
            responses.add(AbsenceResponse(flowEngine.subFlow(AbsenceSubFlow(r.first.counterparty))))
            retries++
        }
        return responses
    }
}

@InitiatingFlow("absence-call")
class AbsenceSubFlow(val counterparty: MemberX500Name) : SubFlow<String> {

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): String {
        val session = flowMessaging.initiateFlow(counterparty)
        session.send(RollCallRequest(counterparty.toString()))
        return session.receive(RollCallResponse::class.java).unwrap {it}.response
    }
}

@InitiatedBy("roll-call")
class RollCallResponderFlow: ResponderFlow {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        ResponderFlowDelegate().callDelegate(session, flowEngine)
    }


}

@InitiatedBy("absence-call")
class AbsenceCallResponderFlow: ResponderFlow {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        ResponderFlowDelegate().callDelegate(session, flowEngine)
    }
}

@CordaSerializable
data class RollCallInitiationRequest(val recipientsX500: List<String>)
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