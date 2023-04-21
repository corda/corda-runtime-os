package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow

/**
 * [FlowFactory] creates [Flow]s.
 */
interface FlowFactory {

    /**
     * Creates a [Flow] from a [StartFlow] request.
     *
     * @param startFlowEvent The [StartFlow] that provides context to the started flow.
     * @param sandboxGroupContext The sandbox to load the [Flow] class from.
     *
     * @return A new [Flow] instance.
     */
    fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): FlowLogicAndArgs

    /**
     * Creates an initiated [Flow].
     *
     * @param flowStartContext The [FlowStartContext] describing the flow.
     * @param sandboxGroupContext The sandbox to load the [Flow] class from.
     * @param contextProperties The context properties to be set on the session which is passed to the initiated flow.
     *
     * @return A new [Flow] instance.
     */
    fun createInitiatedFlow(
        flowStartContext: FlowStartContext,
        sandboxGroupContext: SandboxGroupContext,
        contextProperties: Map<String, String>
    ): FlowLogicAndArgs
}