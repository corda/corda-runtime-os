package net.corda.simulator.runtime.messaging

import net.corda.simulator.exceptions.NoRegisteredResponderException
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.io.Closeable
import kotlin.concurrent.thread

interface CloseableFlowMessaging: FlowMessaging, Closeable

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
 * @param flowContext The context of the flow in which the messaging is taking place
 * @param fiber The "fiber" in which Simulator registered responder flow classes or instances and persistence
 * @param injector The injector for @CordaInject flow services
 * @param flowFactory The factory which will initialize and inject services into the responder flow.
 * @param contextProperties The [FlowContextProperties] for the flow
 */
@Suppress("LongParameterList")
class ConcurrentFlowMessaging(
    private val flowContext: FlowContext,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory,
    private val contextProperties: FlowContextProperties,
    private val sessionFactory: SessionFactory = BaseSessionFactory()
) : CloseableFlowMessaging {

    private val openedSessions = mutableListOf<SessionPair>()

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
        return doInitiate(x500Name, null)
    }

    private fun doInitiate(
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder?): FlowSession {

        val protocol = flowContext.protocol

        log.info("Initiating flow for protocol \"$protocol\" from \"${flowContext.member}\" to \"$x500Name\"")

        val responderClass = fiber.lookUpResponderClass(x500Name, protocol)
        val responderFlow = if (responderClass == null) {
            val foundResponder = fiber.lookUpResponderInstance(x500Name, protocol)
                ?: throw NoRegisteredResponderException(x500Name, protocol)
            log.info("Matched protocol with responder instance $foundResponder")
            foundResponder
        } else {
            log.info("Matched protocol with responder class $responderClass")
            flowFactory.createResponderFlow(x500Name, responderClass)
        }

        val updatedContextProperties =
            flowContextPropertiesBuilder?.let { contextBuilder ->
                copyFlowContextProperties(contextProperties).also {
                    contextBuilder.apply(it)
                }
            }?:
            copyFlowContextProperties(contextProperties)

        injector.injectServices(responderFlow, x500Name, fiber, updatedContextProperties)

        val sessions = sessionFactory.createSessions(x500Name, flowContext,
            flowContextProperties = updatedContextProperties as SimFlowContextProperties)
        openedSessions.add(sessions)

        val (initiatorSession, responderSession) = sessions

        log.info("Starting responder thread for protocol \"$protocol\" from \"${flowContext.member}\" to \"$x500Name\"")
        thread {
            try {
                responderFlow.call(responderSession)
                responderSession.close()
                initiatorSession.responderClosed()
                log.info("Closed responder for protocol \"$protocol\" from \"${flowContext.member}\" to \"$x500Name\"")
            } catch (t: Throwable) {
                log.info("Caught error in protocol \"$protocol\" from \"${flowContext.member}\" to \"$x500Name\"")
                initiatorSession.responderErrorCaught(t)
            }
        }
        return initiatorSession
    }

    override fun initiateFlow(
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder
    ): FlowSession {
        return doInitiate(x500Name, flowContextPropertiesBuilder)
    }

    /**
     * Not yet implemented.
     */
    override fun <R : Any> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<R> {
        requireBoxedType(receiveType)
        return sessions.map { it.receive(receiveType) }
    }

    override fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, Any> {
        sessions.mapKeys { requireBoxedType(it.value) }
        return sessions.mapValues { it.key.receive(it.value) }
    }

    override fun sendAll(payload: Any, sessions: Set<FlowSession>) {
        sessions.forEach { it.send(payload) }
    }

    override fun sendAllMap(payloadsPerSession: Map<FlowSession, Any>) {
        payloadsPerSession.keys.forEach { it.send(payloadsPerSession[it]!!) }
    }

    private fun requireBoxedType(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    override fun close() {
        openedSessions.forEach {
            // Note that this order is important; the initiator waits for the responder to close anyway,
            // which means that it has successfully been called.
            // The second call to close the responder is in case it has errored.
            it.initiatorSession.close()
            it.responderSession.close()
        }
    }

}
