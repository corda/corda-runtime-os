package net.corda.simulator.runtime.messaging

import net.corda.simulator.exceptions.NoRegisteredResponderException
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * FlowMessaging is responsible for sending messages and from other "virtual nodes".
 *
 * ConcurrentFlowMessaging implements a single thread per responder flow (keeping the initiating flow on the calling
 * thread). It creates a pair of sessions for initiator and responder. Each pair of sessions has two shared queues;
 * one for outgoing messages, and one for incoming messages, allowing initiator and responder to be started in any
 * order.
 *
 * Note that the "fiber" must be the same instance for all nodes; it acts as the equivalent of the message bus,
 * allowing nodes to communicate with each other.
 *
 * @see [FlowMessaging] for method docs.
 *
 * @flowContext The context of the flow in which the messaging is taking place
 * @fiber The "fiber" in which Simulator registered responder flow classes or instances and persistence
 * @injector The injector for @CordaInject flow services
 * @flowFactory The factory which will initialize and inject services into the responder flow.
 */
class ConcurrentFlowMessaging(
    private val flowContext: FlowContext,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory
) : FlowMessaging {

    companion object {
        val log = contextLogger()
    }

    /**
     * @see [FlowMessaging] for more details.
     *
     * This implementation matches the context protocol for this instance with a matching responder instance or
     * class previously registered in the fiber (through the [FlowServicesInjector]), constructs [FlowSession]s
     * for both initiator and responder, and calls the responder flow on a new thread. If it detects an error
     * being thrown in that thread, it sets an error condition property on the initiating flow's session.
     *
     * @throws NoRegisteredResponderException if no responder has been registered.
     */
    override fun initiateFlow(x500Name: MemberX500Name): FlowSession {
        val protocol = flowContext.protocol

        log.info("Initiating flow for protocol \"$protocol\" from \"${flowContext.member}\" to \"$x500Name\"")

        val responderClass = fiber.lookUpResponderClass(x500Name, protocol)
        val responderFlow = if (responderClass == null) {
            log.info("Matched protocol with responder instance")
            fiber.lookUpResponderInstance(x500Name, protocol)
                ?: throw NoRegisteredResponderException(x500Name, protocol)
        } else {
            log.info("Matched protocol with responder class $responderClass")
            flowFactory.createResponderFlow(x500Name, responderClass)
        }

        injector.injectServices(responderFlow, x500Name, fiber, flowFactory)

        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()
        val initiatorSession = BlockingQueueFlowSession(
            flowContext.copy(member = x500Name),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )
        val recipientSession = BlockingQueueFlowSession(
            flowContext,
            fromInitiatorToResponder,
            fromResponderToInitiator,
        )

        log.info("Starting responder thread for protocol \"$protocol\" from \"${flowContext.member}\" to \"$x500Name\"")
        thread {
            try {
                responderFlow.call(recipientSession)
            } catch (t: Throwable) {
                initiatorSession.responderErrorCaught(t)
            }
        }
        return initiatorSession
    }

    /**
     * Not yet implemented.
     */
    override fun initiateFlow(
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder
    ): FlowSession {
        TODO("Not yet implemented")
    }

    override fun <R : Any> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<R> {
        TODO("Not yet implemented")
    }

    override fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, Any> {
        TODO("Not yet implemented")
    }

    override fun sendAll(payload: Any, sessions: Set<FlowSession>) {
        TODO("Not yet implemented")
    }

    override fun sendAllMap(payloadsPerSession: Map<FlowSession, Any>) {
        TODO("Not yet implemented")
    }

}
