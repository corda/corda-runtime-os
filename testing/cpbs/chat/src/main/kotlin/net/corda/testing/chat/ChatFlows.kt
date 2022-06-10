package net.corda.testing.chat

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.application.serialization.parseJson
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
@StartableByRPC
class ChatOutgoingFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(): String {
        log.info("Chat outgoing flow starting...")

        val inputs = jsonMarshallingService.parseJson<OutgoingChatMessage>(jsonArg)
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
 * Incoming message flow, instantiated for receiving chat messages by Corda as part of the chat protocol pair. Messages
 * are placed in the message store. To read outstanding messages, poll the ChatReaderFlow.
 */
@InitiatedBy(protocol = "chatProtocol")
class ChatIncomingFlow(private val session: FlowSession) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call(): String {
        log.info("Chat incoming flow starting...")

        val sender = session.counterparty.x500Principal.toString()
        val message = session.receive<MessageContainer>().unwrap { it.message }

        MessageStore.add(IncomingChatMessage(sender, message))

        log.info("Added incoming message to message store")
        return ""
    }
}

/**
 * Returns any outstanding messages unread to the caller. Read messages are removed from the store.
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
@StartableByRPC
class ChatReaderFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(): String {
        log.info("Chat reader flow starting...")
        return jsonMarshallingService.formatJson(MessageStore.readAndClear())
    }
}
