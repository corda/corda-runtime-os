package net.cordapp.testing.calculator

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

/**
 * Outgoing chat message flow, for sending a chat message to another member.
 * JSON argument should look something like:
 * ```json
 * {
 *   "recipientX500Name": "CN=Alice, O=R3, L=London, C=GB",
 *   "message": "Hello Alice"
 * }
 * ```
 */
@InitiatingFlow(protocol = "chatProtocol")
class ChatOutgoingFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Chat outgoing flow starting in ${flowEngine.virtualNodeName}...")
        val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, ChatOutgoingFlowParameter::class.java)
        inputs.recipientX500Name ?: throw IllegalArgumentException("Recipient X500 name not supplied")
        inputs.message ?: throw IllegalArgumentException("Chat message not supplied")

        log.info("Processing chat message destined for ${inputs.recipientX500Name}")

        val session = flowMessaging.initiateFlow(MemberX500Name.parse(inputs.recipientX500Name))
        session.send(MessageContainer(inputs.message))

        log.info("Sent message from ${flowEngine.virtualNodeName} to ${inputs.recipientX500Name}")

        log.info("Waiting for reply...")
        session.receive(MessageContainer::class.java).message
        log.info("Done!")

        return ""
    }
}

/**
 * Incoming message flow, instantiated for receiving chat messages by Corda as part of the declared chat protocol.
 * Messages are placed in the message store. To read outstanding messages, poll the ChatReaderFlow.
 * Messages are limited in size when stored, and there is a limit to the number of messages that can be stored at the
 * same time. When the limit is reached older messages are removed.
 */
@InitiatedBy(protocol = "chatProtocol")
class ChatIncomingFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        val thisVirtualNodeName = flowEngine.virtualNodeName.toString()
        log.info("Chat incoming flow starting in {$thisVirtualNodeName}...")

        val sender = session.counterparty.toString()
        val message = session.receive(MessageContainer::class.java).message

        log.info("Received message '$message' from '$sender'")

        log.info("Waiting for another message...")
        session.receive(MessageContainer::class.java).message
        log.info("Done!")
    }
}
