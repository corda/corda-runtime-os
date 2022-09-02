package net.corda.cordapptestutils.internal.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable

class HelloFlow : RPCStartableFlow {

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

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val name = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService).name
        val greeting = flowEngine.subFlow(object : SubFlow<String> {
            @Suspendable
            override fun call(): String = "Hello"
        })
        return "$greeting $name!"
    }
}

data class InputMessage(val name : String)