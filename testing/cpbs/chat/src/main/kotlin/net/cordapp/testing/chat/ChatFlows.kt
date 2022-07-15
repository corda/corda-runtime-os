package net.cordapp.testing.chat

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

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
class ChatOutgoingFlow : RPCStartableFlow {
    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Chat outgoing flow starting in ${flowEngine.virtualNodeName}...")
        val inputs = requestBody.getRequestBodyAs<ChatOutgoingFlowParameter>(jsonMarshallingService)
        inputs.recipientX500Name ?: throw IllegalArgumentException("Recipient X500 name not supplied")
        inputs.message ?: throw IllegalArgumentException("Chat message not supplied")

        log.info("Processing chat message destined for ${inputs.recipientX500Name}")

        val session = flowMessaging.initiateFlow(MemberX500Name.parse(inputs.recipientX500Name))
        session.send(MessageContainer(inputs.message))

        storeOutgoingMessage(
            persistenceService = persistenceService,
            recipient = inputs.recipientX500Name,
            message = inputs.message
        )

        log.info("Sent message from ${flowEngine.virtualNodeName} to ${inputs.recipientX500Name}")
        return ""
    }
}

/**
 * Incoming message flow, instantiated for receiving chat messages by Corda as part of the declared chat protocol.
 * Messages are placed in the message store. To read outstanding messages, poll the ChatReaderFlow.
 */
@InitiatedBy(protocol = "chatProtocol")
class ChatIncomingFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(session: FlowSession) {
        val thisVirtualNodeName = flowEngine.virtualNodeName.toString()
        log.info("Chat incoming flow starting in {$thisVirtualNodeName}...")

        val sender = session.counterparty.toString()
        val message = session.receive<MessageContainer>().unwrap { it.message }

        storeIncomingMessage(persistenceService, sender, message)

        log.info("Added incoming message from ${sender} to message store")
    }
}

/**
 * Returns all messages, the output will look something like:
 * ```json
 * {
 *   "messages": [
 *     {
 *       "senderX500Name": "CN=Bob, O=R3, L=London, C=GB",
 *       "message": "Hello from Bob"
 *     },
 *     {
 *       "senderX500Name": "CN=Alice, O=R3, L=London, C=GB",
 *       "message": "Hello from Alice"
 *     }
 *   ]
 * }
 * ```
 */
class ChatReaderFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Chat reader flow starting in {$flowEngine.virtualNodeName}...")

        val messages = readAllMessages(persistenceService).also {
            log.info(
                "Returning ${it.receivedChatMessages.messages.size} received messages and" +
                        "${it.sentChatMessages.messages.size} sent messages"
            )
        }
        return jsonMarshallingService.format(messages)
    }
}
