package net.corda.testutils.internal

import net.corda.testutils.exceptions.NoInitiatingFlowAnnotationException
import net.corda.testutils.exceptions.NoRegisteredResponderException
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
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
 * @initiator The initiating flow
 * @flowClass The class of the initiating flow; used for looking up the protocol
 * @protocolLookUp The "fiber" in which FakeCorda registered "uploaded" responder flow classes in place of "virtual nodes"
 * @injector The injector for @CordaInject flow services
 * @flowFactory The factory which will initialize and inject services into the responder flow.
 */
class ConcurrentFlowMessaging(
    private val initiator: MemberX500Name,
    private val flowClass: Class<out Flow>,
    private val fakeFiber: FakeFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory
) : FlowMessaging {
    override fun close(sessions: Set<FlowSession>) {
        TODO("Not yet implemented")
    }

    override fun initiateFlow(x500Name: MemberX500Name): FlowSession {
        val protocol = flowClass.getAnnotation(InitiatingFlow::class.java)?.protocol
            ?: throw NoInitiatingFlowAnnotationException(flowClass)

        val responderClass = fakeFiber.lookUpResponderClass(x500Name, protocol)
        val responderFlow = if (responderClass == null) {
            fakeFiber.lookUpResponderInstance(x500Name, protocol)
                ?: throw NoRegisteredResponderException(x500Name, protocol)
        } else {
            flowFactory.createResponderFlow(x500Name, responderClass)
        }

        injector.injectServices(responderFlow, x500Name, fakeFiber, flowFactory)

        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()
        val initiatorSession = BlockingQueueFlowSession(
            initiator,
            x500Name,
            flowClass,
            fromInitiatorToResponder,
            fromResponderToInitiator)
        val recipientSession = BlockingQueueFlowSession(
            x500Name,
            initiator,
            flowClass,
            fromResponderToInitiator,
            fromInitiatorToResponder)

        thread { responderFlow.call(recipientSession) }
        return initiatorSession
    }

    override fun <R> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<UntrustworthyData<R>> {
        TODO("Not yet implemented")
    }

    override fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, UntrustworthyData<Any>> {
        TODO("Not yet implemented")
    }

    override fun sendAll(payload: Any, sessions: Set<FlowSession>) {
        TODO("Not yet implemented")
    }

    override fun sendAllMap(payloadsPerSession: Map<FlowSession, *>) {
        TODO("Not yet implemented")
    }


}