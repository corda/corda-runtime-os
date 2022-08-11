package net.corda.testutils.internal

import net.corda.testutils.tools.CordaFlowChecker
import net.corda.testutils.tools.FlowChecker
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import java.time.Duration
import java.util.*

/**
 * A flow engine which, for the provided subflow, injects services into that subflow then calls it and returns any
 * response. Note that the FiberMock provided must be able to look up any members that will be contacted by the
 * subflow (so if you're using the ProtocolLookUpFiberMock, it should be the same instance that was provided to
 * the CordaMock).
 *
 * Note that if you're doing anything that complicated, you might want to just use the CordaMock instead.
 *
 * @virtualNodeName the name of the virtual node owner
 * @fiber a FiberMock through which responders should be registered
 * @injector an injector which will initialize the services in the subFlow
 * @flowChecker a flow checker
 *
 * @return the value returned by the subflow when called
 */
class InjectingFlowEngine(
    override val virtualNodeName: MemberX500Name,
    private val fiberFake: FiberFake,
    private val injector: FlowServicesInjector = DefaultServicesInjector(),
    private val flowChecker: FlowChecker = CordaFlowChecker()
) : FlowEngine {
    override val flowId: UUID
        get() = TODO("Not yet implemented")

    override fun sleep(duration: Duration) {
        TODO("Not yet implemented")
    }

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        flowChecker.check(subFlow.javaClass)
        injector.injectServices(subFlow, virtualNodeName, fiberFake)
        return subFlow.call()
    }

}
