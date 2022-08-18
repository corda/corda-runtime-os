package net.corda.testutils

import net.corda.testutils.internal.BaseFakeFiber
import net.corda.testutils.internal.BaseFlowFactory
import net.corda.testutils.internal.DefaultServicesInjector
import net.corda.testutils.internal.FakeFiber
import net.corda.testutils.internal.FlowFactory
import net.corda.testutils.internal.FlowServicesInjector
import net.corda.testutils.internal.cast
import net.corda.testutils.tools.CordaFlowChecker
import net.corda.testutils.tools.FlowChecker
import net.corda.testutils.tools.RPCRequestDataWrapper
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import java.io.Closeable

/**
 * This is a fake of the Corda network which will run in-process. It allows a fake "virtual node" to be created
 * by "uploading" a particular flow class for a `MemberX500Name`. Note that the upload does not have to be symmetrical;
 * an initiating flow class can be registered for one party with a responding flow class registered for another.
 *
 * The FakeCorda uses three fake services to help mimic the Corda network while ensuring that your flow will work well
 * with the real thing. These can be mocked out or wrapped if required, but most of the time the defaults will be
 * enough.
 *
 * Instances of initiator or responder flows can also be uploaded; however, these will not undergo the same checks
 * as a class upload. This should generally only be used for mocked or faked flows to test matching responder or
 * initiator flows in isolation.
 *
 * @flowChecker Checks any flow class. Defaults to checking the various hooks which the real Corda would require
 * @flowServices A factory for constructing services that will be injected into the flows
 * @fiberMock The "fiber" with which responder flows will be registered by protocol
 */
class FakeCorda(
    private val flowChecker: FlowChecker = CordaFlowChecker(),
    private val fakeFiber: FakeFiber = BaseFakeFiber(),
    private val injector: FlowServicesInjector = DefaultServicesInjector()
) : Closeable {

    private val flowFactory: FlowFactory = BaseFlowFactory()

    /**
     * Registers a flow class against a given member with the FakeCorda. Flow classes "uploaded" here will be checked
     * for validity. Responder flows will also be registered with the "fiber".
     *
     * @member The member for whom this flow will be registered.
     * @flowClass The flow to register. Must be an `RPCStartableFlow` or a `ResponderFlow`.
     */
    fun createVirtualNode(holdingIdentity: HoldingIdentity, vararg flowClasses: Class<out Flow>) : VirtualNodeInfo {
        flowClasses.forEach {
            flowChecker.check(it)
            registerAnyResponderWithFiber(holdingIdentity.member, it)
        }
        return VirtualNodeInfo(holdingIdentity)
    }

    private fun registerAnyResponderWithFiber(
        member: MemberX500Name,
        flowClass: Class<out Flow>
    ) {
        val protocolIfResponder = flowClass.getAnnotation(InitiatedBy::class.java)?.protocol
        if (protocolIfResponder != null) {
            val responderFlowClass = castInitiatedFlowToResponder(flowClass)
            fakeFiber.registerResponderClass(member, protocolIfResponder, responderFlowClass)
        }
    }

    private fun castInitiatedFlowToResponder(flowClass: Class<out Flow>) =
        cast<Class<out ResponderFlow>>(flowClass) ?: throw IllegalArgumentException(
            "${flowClass.simpleName} has an @${InitiatedBy::class.java} annotation, but " +
                    "it is not a ${ResponderFlow::class.java}"
        )

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @initiator the holding identity of the initating node (not the responder(s), whose identities should be
     * parsed from input data)
     * @input the data to input to the flow
     *
     * @return the response from the flow
     */
    fun callFlow(initiator: VirtualNodeInfo, input: RPCRequestDataWrapper): String {
        val flowClassName = input.flowClassName
        val flow = flowFactory.createInitiatingFlow(initiator.member, flowClassName)
        injector.injectServices(flow, initiator.member, fakeFiber, flowFactory)
        return flow.call(input.toRPCRequestData())
    }

    /**
     * Uploads a concrete instance of a responder flow.
     */
    fun createVirtualNode(responder: HoldingIdentity, protocol: String, responderFlow: ResponderFlow) {
        fakeFiber.registerResponderInstance(responder.member, protocol, responderFlow)
    }

    fun getPersistenceServiceFor(virtualNodeInfo: VirtualNodeInfo): PersistenceService =
        fakeFiber.getOrCreatePersistenceService(virtualNodeInfo.member)

    override fun close() {
        fakeFiber.close()
    }
}