package net.corda.simulator.runtime.flows

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.copyFlowContextProperties
import net.corda.simulator.runtime.tools.CordaFlowChecker
import net.corda.simulator.tools.FlowChecker
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
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
 * @param userContextProperties The [FlowContextProperties] for the flow.
 * @param injector An injector which will initialize the services in the subFlow.
 * @param flowChecker A flow checker.
 *
 * @return The value returned by the subflow when called.
 */
@Suppress("LongParameterList")
class InjectingFlowEngine(
    private val configuration: SimulatorConfiguration,
    private val virtualNodeName: MemberX500Name,
    private val fiber: SimFiber,
    userContextProperties: FlowContextProperties,
    private val injector: FlowServicesInjector = DefaultServicesInjector(configuration),
    private val flowChecker: FlowChecker = CordaFlowChecker(),
    private val flowManager: FlowManager = BaseFlowManager()
) : FlowEngine {

    private val userContextProperties = copyFlowContextProperties(userContextProperties)
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getVirtualNodeName(): MemberX500Name = virtualNodeName

    override fun getFlowId(): UUID = TODO("Not yet implemented")

    override fun getFlowContextProperties(): FlowContextProperties = userContextProperties

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        log.info("Running subflow ${SubFlow::class.java} for \"$virtualNodeName\"")
        flowChecker.check(subFlow.javaClass)
        injector.injectServices(
            FlowAndProtocol(subFlow),
            virtualNodeName,
            fiber,
            copyFlowContextProperties(userContextProperties)
        )
        val result = flowManager.call(subFlow)
        log.info("Finished subflow ${SubFlow::class.java} for \"$virtualNodeName\"")
        return result
    }
}
