package net.cordapp.testing.chat

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.json.JsonNodeReaderType
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.NewInterface
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
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

    @CordaInject
    lateinit var persistenceService: PersistenceService

    class NewInterfaceImpl : NewInterface {
        override fun someFunction1() {
            log.info("@@@ someFunction1")
        }
    }

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("Chat outgoing flow starting in ${flowEngine.virtualNodeName}...")
        val inputs = requestBody.getRequestBodyAs<ChatOutgoingFlowParameter>(jsonMarshallingService)
        inputs.recipientX500Name ?: throw IllegalArgumentException("Recipient X500 name not supplied")
        inputs.message ?: throw IllegalArgumentException("Chat message not supplied")

        if (jsonMarshallingService.returnSomeReaderTypeEnum() != JsonNodeReaderType.ARRAY) {
            throw CordaRuntimeException("enum is bad!")
        }

        val newInterface = NewInterfaceImpl()

        repeat(10000) {
            log.info("Processing chat message destined for ${inputs.recipientX500Name} iteration $it")

            val session = flowMessaging.initiateFlow(MemberX500Name.parse(inputs.recipientX500Name))
            session.send(MessageContainer(inputs.message + " iteration $it"))

            session.acceptNewInterface(newInterface)

            storeOutgoingMessage(
                persistenceService = persistenceService, recipient = inputs.recipientX500Name, message = inputs.message
            )

            log.info("Sent message from ${flowEngine.virtualNodeName} to ${inputs.recipientX500Name}")
            session.close()
        }
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

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(session: FlowSession) {
        val thisVirtualNodeName = flowEngine.virtualNodeName.toString()
        log.info("Chat incoming flow starting in {$thisVirtualNodeName}...")

        val sender = session.counterparty.toString()
        val message = session.receive<MessageContainer>().message

        storeIncomingMessage(persistenceService, sender, message)

        log.info("Added incoming message from $sender to message store")
    }
}

/**
 * Returns all messages, the output will look something like:
 * ```json
 * [
 *   {
 *     "counterparty": "CN=Alice, O=R3, L=London, C=GB",
 *     "messages": [
 *       {
 *         "id": "4c6ce4a8-cb5a-41b9-8476-d1310b298d19",
 *         "direction": "incoming",
 *         "content": "Hello Bob",
 *         "timestamp": "1658137194"
 *       },
 *       {
 *         "id": "a0b15c2a-fae3-44c4-a7ff-5dbad90a23b3",
 *         "direction": "incoming",
 *         "content": "How are you?",
 *         "timestamp": "1658137263"
 *       },
 *       {
 *         "id": "dda15e36-b69e-43ee-b17a-d0105505a232",
 *         "direction": "outgoing",
 *         "content": "I'm fine thinks Alice!",
 *         "timestamp": "1658137304"
 *       }
 *     ]
 *   }
 * ]
 * ```
 * Messages are returned in time order, with outgoing and incoming messages interleaved in the same array.
 */
class ChatReaderFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("Chat reader flow starting in {$flowEngine.virtualNodeName}...")

        val messages = readAllMessages(persistenceService).also {
            log.info("Returning ${it.size} chats")
        }
        return jsonMarshallingService.format(messages)
    }
}
