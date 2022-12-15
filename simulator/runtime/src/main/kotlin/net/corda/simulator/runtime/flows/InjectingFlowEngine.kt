package net.corda.simulator.runtime.flows

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.tools.CordaFlowChecker
import net.corda.simulator.tools.FlowChecker
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.util.UUID

/**
 * A flow engine which, for the provided subflow, injects services into that subflow then calls it and returns any
 * response. Note that the simulated fiber provided must be able to look up any members that will be contacted by the
 * subflow (so if you're using the BaseSimFiber, it should be the same instance that was provided to
 * Simulator).
 *
 * @param configuration The configuration of the instance of Simulator.
 * @param virtualNodeName The name of the virtual node owner.
 * @param fiber A simulated fiber through which responders should be registered.
 * @param injector An injector which will initialize the services in the subFlow.
 * @param flowChecker A flow checker.
 *
 * @return The value returned by the subflow when called.
 */
@Suppress("LongParameterList")
class InjectingFlowEngine(
    private val configuration: SimulatorConfiguration,
    override val virtualNodeName: MemberX500Name,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector = DefaultServicesInjector(configuration),
    private val flowChecker: FlowChecker = CordaFlowChecker(),
    private val flowManager: FlowManager = BaseFlowManager()
) : FlowEngine {
    companion object {
        val log = contextLogger()
    }

    override val flowId: UUID
        get() = TODO("Not yet implemented")

    override val flowContextProperties: FlowContextProperties
        get() = TODO("Not yet implemented")

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        log.info("Running subflow ${SubFlow::class.java} for \"$virtualNodeName\"")
        flowChecker.check(subFlow.javaClass)
        injector.injectServices(subFlow, virtualNodeName, fiber)
        val result = flowManager.call(subFlow)
        log.info("Finished subflow ${SubFlow::class.java} for \"$virtualNodeName\"")
        return result
    }
}
