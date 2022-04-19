package net.corda.flowworker.development.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

@InitiatingFlow
@StartableByRPC
class MessagingFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): String {
        log.info("Hello world is starting... [${flowEngine.flowId}]")
        val session = flowMessaging.initiateFlow(
            MemberX500Name(
                commonName = "Alice",
                organisation = "Alice Corp",
                locality = "LDN",
                country = "GB"
            )
        )

        val received = session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1)).unwrap { it }

        log.info("Received data from initiated flow 1: $received")

        flowEngine.subFlow(InlineSubFlow(session))

        flowEngine.subFlow(InitiatingSubFlow())

        log.info("Finished initiating subflow")

        val received3 = session.receive<MyClass>().unwrap { it }

        log.info("Received data from initiated flow 3: $received3")

        session.close()

        log.info("Closed session")
        log.info("Hello world completed.")

        return "finished top level flow"
    }
}

@InitiatedBy(MessagingFlow::class)
class MessagingInitiatedFlow(private val session: FlowSession) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): String {
        log.info("I have been called [${flowEngine.flowId}]")

        val received = session.receive<MyClass>().unwrap { it }

        log.info("Received data from peer: $received")

        session.send(received.copy(string = "this is a new object", int = 2))

        val received2 = session.receive<MyClass>().unwrap { it }

        log.info("Received data from peer 2: $received2")

        session.send(received.copy(string = "this is a new object 2", int = 2))

        session.send(received.copy(string = "this is a new object 3", int = 2))

        log.info("Closing session")

        session.close()

        log.info("Closed session")

        return "finished top level initiated flow"
    }
}

class InlineSubFlow(private val session: FlowSession) : Flow<Unit> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call() {
        log.info("Inline subFlow is starting...")
        val received = session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1)).unwrap { it }

        log.info("Received data from initiated flow 2 (inlined subFlow): $received")
    }
}

@InitiatingFlow
class InitiatingSubFlow : Flow<Unit> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call() {
        log.info("Initiating subFlow is starting...")
        val session = flowMessaging.initiateFlow(
            MemberX500Name(
                commonName = "Alice",
                organisation = "Alice Corp",
                locality = "LDN",
                country = "GB"
            )
        )

        val received = session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1)).unwrap { it }

        log.info("Received data from initiated subFlow: $received")
    }
}

@InitiatedBy(InitiatingSubFlow::class)
class InitiatingSubFlowInitiatedFlow(private val session: FlowSession) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): String {
        log.info("I have been called [${flowEngine.flowId}]")

        val received = session.receive<MyClass>().unwrap { it }

        log.info("Received data from peer: $received")

        session.send(received.copy(string = "this is a new object", int = 2))

        // should explode when we implement more close logic
//        session.receive<MyClass>().unwrap { it }

        return "finished initiated subflow flow"
    }
}

@CordaSerializable
data class MyClass(
    val string: String,
    val int: Int
)