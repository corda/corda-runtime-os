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
 * {
 *   "recipientX500Name": "CN=Alice, O=R3, L=London, C=GB",
 *   "message": "Hello Alice"
 * }
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

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Chat outgoing flow starting in ${flowEngine.virtualNodeName}...")
        val inputs = requestBody.getRequestBodyAs<ChatOutgoingFlowParameter>(jsonMarshallingService)
        inputs.recipientX500Name ?: throw IllegalArgumentException("Recipient X500 name not supplied")
        inputs.message ?: throw IllegalArgumentException("Chat message not supplied")

        log.info("Processing chat message destined for ${inputs.recipientX500Name}")

        val session = flowMessaging.initiateFlow(MemberX500Name.parse(inputs.recipientX500Name))
        session.send(MessageContainer(inputs.message))

        log.info("Sent message to recipient")
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

        MessageStore.add(persistenceService, IncomingChatMessage(sender, message))

        log.info("Added incoming message from ${sender} to message store")
    }
}

/**
 * Returns any outstanding messages unread for the virtual node member. Read messages are removed from the store thus it
 * becomes the responsibility of the caller to keep track of them after this point. This mechanism allows a client to
 * poll for new messages to a member repeatedly, however precludes multiple clients reading chats to the same member.
 * As an input parameter, the originating sender for which you wish to read messages much be supplied.
 * JSON argument should look something like:
 * {
 *   "recipientX500Name": "CN=Alice, O=R3, L=London, C=GB"
 * }
 *
 * The output will look something like:
 * {
 *   "messages": [
 *     {
 *       "senderX500Name": "CN=Bob, O=R3, L=London, C=GB",
 *       "message": "Hello from Bob"
 *     },
 *     {
 *       "senderX500Name": "CN=Charlie, O=R3, L=London, C=GB",
 *       "message": "Hello from Charlie"
 *     }
 *   ]
 * }
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

        val inputs = requestBody.getRequestBodyAs<ChatReaderFlowParameter>(jsonMarshallingService)
        inputs.fromName ?: throw IllegalArgumentException("Recipient X500 name not supplied")

        with(MessageStore.readAndClear(persistenceService, inputs.fromName)) {
            log.info("Returning ${this.messages.size} unread messages")
            return jsonMarshallingService.format(this)
        }
    }
}
