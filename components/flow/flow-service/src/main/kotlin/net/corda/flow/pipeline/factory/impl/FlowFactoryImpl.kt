package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.event.StartFlow
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Component

@Component(service = [FlowFactory::class])
@Suppress("Unused")
class FlowFactoryImpl : FlowFactory {

    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): Flow<*> {
        val flowClass: Class<Flow<*>> =
            uncheckedCast(
                sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                    startFlowEvent.startContext.flowClassName,
                    Flow::class.java
                )
            )

        return flowClass
            .getDeclaredConstructor(String::class.java)
            .newInstance(startFlowEvent.flowStartArgs)
    }

    override fun createInitiatedFlow(sandboxGroupContext: SandboxGroupContext, flowClassName: String, flowSession: FlowSession): Flow<*> {
        val flowClass: Class<Flow<*>> = uncheckedCast(
            sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                flowClassName,
                Flow::class.java
            )
        )

        return flowClass
            .getDeclaredConstructor(FlowSession::class.java)
            .newInstance(flowSession)
    }
}

