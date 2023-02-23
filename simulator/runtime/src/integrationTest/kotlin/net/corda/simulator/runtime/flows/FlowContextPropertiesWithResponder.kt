package net.corda.simulator.runtime.flows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

/**
 * This flow calls responder flow with different members, each responder flow gets its own copy of
 * [FlowContextProperties]. The responder further calls a subflows which also gets its own copy of
 * [FlowContextProperties]
 */
@InitiatingFlow(protocol = "flow-context-2")
class FlowContextPropertiesInitiator : ClientStartableFlow{
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        flowEngine.flowContextProperties.put("key-1", "initiator")
        val participants = requestBody.getRequestBodyAs(jsonMarshallingService, FlowRequest::class.java).participants

        val flowSession1 = flowMessaging.initiateFlow(participants[0])
        flowSession1.send(Member.BOB)
        @Suppress("unchecked_cast")
        val contextPropertiesMap = flowSession1.receive(HashMap::class.java) as HashMap<String, FlowContextProperties>

        flowEngine.flowContextProperties.put("key-2", "initiator")

        val flowSession2 = flowMessaging.initiateFlow(participants[1]){ flowContextProperties ->
            flowContextProperties.put("key-4", "from-builder")
        }
        flowSession2.send(Member.CHARLIE)
        @Suppress("unchecked_cast")
        contextPropertiesMap.putAll(flowSession2.receive(HashMap::class.java) as HashMap<String, FlowContextProperties>)


        contextPropertiesMap["initiator"] = flowEngine.flowContextProperties

        return buildResponseString(listOf("initiator", "bob", "charlie", "bob-subflow", "charlie-subflow"),
            contextPropertiesMap)
    }
}

@InitiatedBy(protocol = "flow-context-2")
class FlowContextPropertiesResponder : ResponderFlow {
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        flowEngine.flowContextProperties.put("key-2", "bob-charlie")
        val member = session.receive(Member::class.java)
        val contextPropertiesMap = HashMap<String, FlowContextProperties>()
        if(member == Member.BOB){
            flowEngine.flowContextProperties.put("key-3", "bob")
            val contextPropertiesFromSubFlow = flowEngine.subFlow(FlowContextPropertiesResponderSubFlow1())
            contextPropertiesMap["bob"] = flowEngine.flowContextProperties
            contextPropertiesMap["bob-subflow"] = contextPropertiesFromSubFlow
            session.send(contextPropertiesMap)
        }else if(member == Member.CHARLIE){
            flowEngine.flowContextProperties.put("key-3", "charlie")
            val contextPropertiesFromSubFlow = flowEngine.subFlow(FlowContextPropertiesResponderSubFlow2())
            contextPropertiesMap["charlie"] = flowEngine.flowContextProperties
            contextPropertiesMap["charlie-subflow"] = contextPropertiesFromSubFlow
            session.send(contextPropertiesMap)
        }

    }
}

@InitiatingFlow(protocol = "flow-context-3")
class FlowContextPropertiesResponderSubFlow1 : SubFlow<FlowContextProperties> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): FlowContextProperties {
        flowEngine.flowContextProperties.put("key-4", "bob-subflow")
        return flowEngine.flowContextProperties
    }

}

@InitiatingFlow(protocol = "flow-context-3")
class FlowContextPropertiesResponderSubFlow2 : SubFlow<FlowContextProperties> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): FlowContextProperties {
        flowEngine.flowContextProperties.put("key-4", "charlie-subflow")
        return flowEngine.flowContextProperties
    }

}

@CordaSerializable
data class FlowRequest(val participants: List<MemberX500Name>)