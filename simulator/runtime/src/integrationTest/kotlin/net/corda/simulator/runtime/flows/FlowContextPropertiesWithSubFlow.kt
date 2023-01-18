package net.corda.simulator.runtime.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.flows.set
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

/**
 * This flow calls nested subflow, Each subflow has its own copy of [FlowContextProperties]. A sublow also calls
 * responder flows which gets their own copy of [FlowContextProperties]
 */
@InitiatingFlow(protocol = "flow-context-1")
class FlowContextPropertiesMainFlow : ClientStartableFlow {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        flowEngine.flowContextProperties["key-1"] = "main-flow"
        flowEngine.flowContextProperties["key-2"] = "main-flow"
        val contextPropertiesMap = flowEngine.subFlow(FlowContextPropertiesSubFlow1())
        contextPropertiesMap["main-flow"] = flowEngine.flowContextProperties

        return buildResponseString(
            listOf("main-flow", "subflow-1", "subflow-2", "bob", "charlie"), contextPropertiesMap)
    }
}

@InitiatingFlow(protocol = "flow-context-2")
class FlowContextPropertiesSubFlow1 : SubFlow<HashMap<String, FlowContextProperties>> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): HashMap<String, FlowContextProperties> {
        flowEngine.flowContextProperties["key-2"] = "subflow-1"

        val contextPropertiesFromSubFlow = flowEngine.subFlow(FlowContextPropertiesSubFlow2())
        val contextPropertiesMap: HashMap<String, FlowContextProperties> = HashMap()

        val bob = MemberX500Name.parse("CN=Bob, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB")
        val bobSession = flowMessaging.initiateFlow(bob)
        bobSession.send(Member.BOB)


        val charlie = MemberX500Name.parse("CN=Charlie, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB")
        val charlieSession = flowMessaging.initiateFlow(charlie){flowContextProperties ->
            flowContextProperties.put("key-4", "from-builder")
        }
        charlieSession.send(Member.CHARLIE)

        val bobContextProperties = bobSession.receive<FlowContextProperties>()
        val charlieContextProperties = charlieSession.receive<FlowContextProperties>()

        flowEngine.flowContextProperties["key-3"] = "subflow-1"

        contextPropertiesMap["subflow-1"] = flowEngine.flowContextProperties
        contextPropertiesMap["subflow-2"] = contextPropertiesFromSubFlow
        contextPropertiesMap["bob"] = bobContextProperties
        contextPropertiesMap["charlie"] = charlieContextProperties

        return contextPropertiesMap
    }

}

@InitiatingFlow(protocol = "flow-context-3")
class FlowContextPropertiesSubFlow2 : SubFlow<FlowContextProperties> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): FlowContextProperties {
        flowEngine.flowContextProperties["key-3"] = "subflow-2"
        return flowEngine.flowContextProperties
    }
}

@InitiatedBy("flow-context-2")
class FlowContextPropertiesSubFlowResponder : ResponderFlow {
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        val member = session.receive<Member>()
        if(member == Member.BOB){
            flowEngine.flowContextProperties["key-3"] = "bob"
            session.send(flowEngine.flowContextProperties)
        }else if(member == Member.CHARLIE){
            flowEngine.flowContextProperties["key-3"] = "charlie"
            session.send(flowEngine.flowContextProperties)
        }
    }
}

enum class Member{
    BOB,
    CHARLIE
}