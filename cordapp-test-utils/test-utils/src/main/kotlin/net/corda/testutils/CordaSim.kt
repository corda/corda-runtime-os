package net.corda.testutils

import net.corda.testutils.internal.BaseSimFiber
import net.corda.testutils.internal.BaseFlowFactory
import net.corda.testutils.internal.DefaultServicesInjector
import net.corda.testutils.internal.SimFiber
import net.corda.testutils.internal.SimVirtualNodeBase
import net.corda.testutils.internal.FlowFactory
import net.corda.testutils.internal.FlowServicesInjector
import net.corda.testutils.internal.cast
import net.corda.testutils.tools.CordaFlowChecker
import net.corda.testutils.tools.FlowChecker
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import java.io.Closeable

/**
 * This is a simulated Corda network which will run in-process. It allows a lightweight "virtual node" to be created
 * which can run flows against a given member. Note that the flows in nodes do not have to be symmetrical;
 * an initiating flow class can be registered for one party with a responding flow class registered for another.
 *
 * The CordaSim uses lightweight versions of Corda services to help mimic the Corda network while ensuring that your
 * flow will work well with the real thing. These can be mocked out or wrapped if required, but most of the time the
 * defaults will be enough.
 *
 * Instances of initiator and responder flows can also be "uploaded"; however, these will not undergo the same checks
 * as a flow class "upload". This should generally only be used for mocked or faked flows to test matching responder or
 * initiator flows in isolation.
 *
 * @flowChecker Checks any flow class. Defaults to checking the various hooks which the real Corda would require
 * @fiber The simulated "fiber" with which responder flows will be registered by protocol
 * @injector An injector to initialize services annotated with @CordaInject in flows and subflows
 */
class CordaSim(
    private val flowChecker: FlowChecker = CordaFlowChecker(),
    private val fiber: SimFiber = BaseSimFiber(),
    private val injector: FlowServicesInjector = DefaultServicesInjector()
) : Closeable {

    private val flowFactory: FlowFactory = BaseFlowFactory()

    /**
     * Registers a flow class against a given member with the CordaSim. Flow classes will be checked
     * for validity. Responder flows will also be registered against their protocols.
     *
     * @member The member for whom this node will be created.
     * @flowClasses The flows which will be available to run in the nodes. Must be `RPCStartableFlow`
     * or `ResponderFlow`.
     */
    fun createVirtualNode(holdingIdentity: HoldingIdentity, vararg flowClasses: Class<out Flow>) : SimVirtualNode {
        flowClasses.forEach {
            flowChecker.check(it)
            registerAnyResponderWithFiber(holdingIdentity.member, it)
        }
        return SimVirtualNodeBase(holdingIdentity, fiber, injector, flowFactory)
    }

    private fun registerAnyResponderWithFiber(
        member: MemberX500Name,
        flowClass: Class<out Flow>
    ) {
        val protocolIfResponder = flowClass.getAnnotation(InitiatedBy::class.java)?.protocol
        if (protocolIfResponder != null) {
            val responderFlowClass = castInitiatedFlowToResponder(flowClass)
            fiber.registerResponderClass(member, protocolIfResponder, responderFlowClass)
        }
    }

    private fun castInitiatedFlowToResponder(flowClass: Class<out Flow>) =
        cast<Class<out ResponderFlow>>(flowClass) ?: throw IllegalArgumentException(
            "${flowClass.simpleName} has an @${InitiatedBy::class.java} annotation, but " +
                    "it is not a ${ResponderFlow::class.java}"
        )

    /**
     * Creates a virtual node holding a concrete instance of a responder flow. Note that this bypasses all
     * checks for constructor and annotations on the flow.
     */
    fun createVirtualNode(responder: HoldingIdentity, protocol: String, responderFlow: ResponderFlow) : SimVirtualNode {
        fiber.registerResponderInstance(responder.member, protocol, responderFlow)
        return SimVirtualNodeBase(responder, fiber, injector, flowFactory)
    }

    override fun close() {
        fiber.close()
    }
}