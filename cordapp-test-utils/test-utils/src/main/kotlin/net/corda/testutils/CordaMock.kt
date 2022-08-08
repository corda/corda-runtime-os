package net.corda.testutils

import net.corda.testutils.internal.BaseFiberMock
import net.corda.testutils.internal.BaseFlowFactory
import net.corda.testutils.internal.FiberMock
import net.corda.testutils.internal.FlowFactory
import net.corda.testutils.internal.FlowServicesInjector
import net.corda.testutils.internal.SensibleServicesInjector
import net.corda.testutils.internal.cast
import net.corda.testutils.tools.CordaFlowChecker
import net.corda.testutils.tools.FlowChecker
import net.corda.testutils.tools.RPCRequestDataMock
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

/**
 * This is a fake of the Corda network which will run in-process. It allows a fake "virtual node" to be created
 * by "uploading" a particular flow class for a `MemberX500Name`. Note that the upload does not have to be symmetrical;
 * an initiating flow class can be registered for one party with a responding flow class registered for another.
 *
 * The CordaMock uses three fake services to help mimic the Corda network while ensuring that your flow will work well
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
class CordaMock(
    private val flowChecker: FlowChecker = CordaFlowChecker(),
    private val fiberMock: FiberMock = BaseFiberMock(),
    private val injector: FlowServicesInjector = SensibleServicesInjector()
) {

    private val flowFactory: FlowFactory = BaseFlowFactory()

    /**
     * Registers a flow class against a given member with the CordaMock. Flow classes "uploaded" here will be checked
     * for validity. Responder flows will also be registered with the "fiber".
     *
     * @x500 The member for whom this flow will be registered.
     * @flowClass The flow to register. Must be an `RPCStartableFlow` or a `ResponderFlow`.
     */
    fun upload(x500: MemberX500Name, flowClass: Class<out Flow>) {
        flowChecker.check(flowClass)
        registerAnyResponderWithFiber(x500, flowClass)
    }

    private fun registerAnyResponderWithFiber(
        x500: MemberX500Name,
        flowClass: Class<out Flow>
    ) {
        val protocolIfResponder = flowClass.getAnnotation(InitiatedBy::class.java)?.protocol
        if (protocolIfResponder != null) {
            val responderFlowClass = castInitiatingFlowToResponder(flowClass)
            fiberMock.registerResponderClass(x500, protocolIfResponder, responderFlowClass)
        }
    }

    private fun castInitiatingFlowToResponder(flowClass: Class<out Flow>) =
        cast<Class<out ResponderFlow>>(flowClass) ?: throw IllegalArgumentException(
            "${flowClass.simpleName} has an @${InitiatedBy::class.java} annotation, but " +
                    "it is not a ${ResponderFlow::class.java}"
        )

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @x500 the name of the initating node (not the responder(s), whose x500 names should be parsed from input data)
     * @input the data to input to the flow
     *
     * @return the response from the flow
     */
    fun invoke(initiator: MemberX500Name, input: RPCRequestDataMock): String {
        val flowClassName = input.flowClassName
        val flow = flowFactory.createInitiatingFlow(initiator, flowClassName)
        injector.injectServices(flow, initiator, fiberMock, flowFactory)
        return flow.call(input.toRPCRequestData())
    }

    /**
     * Uploads a concrete instance of a responder flow.
     */
    fun upload(x500: MemberX500Name, protocol: String, responder: ResponderFlow) {
        fiberMock.registerResponderInstance(x500, protocol, responder)
    }

    fun getPersistenceServiceFor(x500: MemberX500Name): PersistenceService = fiberMock.getPersistenceService(x500)
}