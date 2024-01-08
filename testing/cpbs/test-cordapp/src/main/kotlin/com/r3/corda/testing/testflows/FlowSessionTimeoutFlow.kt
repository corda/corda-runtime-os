package com.r3.corda.testing.testflows

import com.r3.corda.testing.testflows.messages.FlowTimeoutInput
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.FlowSessionConfiguration
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.time.Duration


@InitiatingFlow(protocol = "flowTimeoutProtocol")
class FlowSessionTimeoutFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService


    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Flow timeout flow is starting... [${flowEngine.flowId}]")
        val input = requestBody.getRequestBodyAs(jsonMarshallingService, FlowTimeoutInput::class.java)
        val counterparty = MemberX500Name.parse(input.counterparty)
        val findCounterparty = memberLookupService.lookup(counterparty)
            ?: throw IllegalStateException("Failed to lookup the member $counterparty")

        val timeout = input.timeout
        val session = if(timeout !=null) {
            log.info("Flow timeout flow is initiating with a timeout of $timeout")
            val config = FlowSessionConfiguration.Builder().timeout(Duration.ofMillis(timeout)).build()
            flowMessaging.initiateFlow(findCounterparty.name, config)
        } else {
            log.info("Flow timeout flow is initiating with default timeout.")
            flowMessaging.initiateFlow(findCounterparty.name)
        }

        session.receive(MyClass::class.java)

        session.close()
        return "finished top level flow"
    }
}

@InitiatedBy(protocol = "flowTimeoutProtocol")
class FlowSessionTimeoutInitiatedFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("I have been called [${flowEngine.flowId}]")

        session.receive(MyClass::class.java)
    }
}
