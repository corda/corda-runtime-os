package net.corda.testutils.internal

import net.corda.testutils.tools.CordaFlowChecker
import net.corda.testutils.tools.FlowChecker
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import java.util.UUID

/**
 * A flow engine which, for the provided subflow, injects services into that subflow then calls it and returns any
 * response. Note that the simulated fiber provided must be able to look up any members that will be contacted by the
 * subflow (so if you're using the BaseSimFiber, it should be the same instance that was provided to
 * the CordaSim).
 *
 * Note that if you're doing anything that complicated, you might want to just use the CordaSim instead.
 *
 * @virtualNodeName the name of the virtual node owner
 * @fiber a simulated fiber through which responders should be registered
 * @injector an injector which will initialize the services in the subFlow
 * @flowChecker a flow checker
 *
 * @return the value returned by the subflow when called
 */
class InjectingFlowEngine(
    override val virtualNodeName: MemberX500Name,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector = DefaultServicesInjector(),
    private val flowChecker: FlowChecker = CordaFlowChecker()
) : FlowEngine {
    override val flowId: UUID
        get() = TODO("Not yet implemented")

    override val flowContextProperties: FlowContextProperties
        get() = TODO("Not yet implemented")

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        flowChecker.check(subFlow.javaClass)
        injector.injectServices(subFlow, virtualNodeName, fiber)
        return subFlow.call()
    }
}
