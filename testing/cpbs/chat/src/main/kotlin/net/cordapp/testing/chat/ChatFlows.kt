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
 * <pre>
 * {
 *   "recipientX500Name": "CN=Alice, O=R3, L=London, C=GB",
 *   "message": "Hello Alice"
 * }
 * </pre>
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

        add(persistenceService, IncomingChatMessage(sender, message))

        log.info("Added incoming message from ${sender} to message store")
    }
}

/**
 * Returns the last unread message(s) for the virtual node member in which the Flow is started. If a "fromName" is
 * supplied it will return the last unread message which originated at the supplied member. If an empty object {} is
 * passed or fromName is empty, all last unread messages from all originating members will be returned.
 * Read messages are removed from the store thus it becomes the responsibility of the caller to keep track of them after
 * this point.
 * JSON argument should look something like:
 * <pre>
 * {
 *   "fromName": "CN=Alice, O=R3, L=London, C=GB"
 * }
 * </pre>
 * or for all messages:
 * <pre>
 * {}
 * </pre>
 *
 * The output will look something like:
 * <pre>
 * {
 *   "messages": [
 *     {
 *       "senderX500Name": "CN=Bob, O=R3, L=London, C=GB",
 *       "message": "Hello from Bob"
 *     }
 *   ]
 * }
 * </pre>
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

        val messages = if (inputs.fromName == null || inputs.fromName.isEmpty()) {
            readAllAndClear(persistenceService).also {
                log.info("Returning ${it.messages.size} unread messages from all senders")
            }
        } else {
            readAndClear(persistenceService, inputs.fromName).also {
                log.info("Returning ${it.messages.size} unread messages from ${inputs.fromName}")
            }
        }

        return jsonMarshallingService.format(messages)
    }
}
