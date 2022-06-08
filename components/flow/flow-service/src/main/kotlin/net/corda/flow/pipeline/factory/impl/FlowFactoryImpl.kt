package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.RPCStartedFlow
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowFactory::class])
@Suppress("Unused")
class FlowFactoryImpl  @Activate constructor(
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory
): FlowFactory {

    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): FlowLogicAndArgs {
        val flowClass: Class<RPCStartableFlow> =
            uncheckedCast(
                sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                    startFlowEvent.startContext.flowClassName,
                    RPCStartableFlow::class.java
                )
            )
        val logic = flowClass.getDeclaredConstructor().newInstance()
        val args = startFlowEvent.flowStartArgs

        return RPCStartedFlow(logic, args)
    }

    override fun createInitiatedFlow(flowStartContext: FlowStartContext, sandboxGroupContext: SandboxGroupContext): FlowLogicAndArgs {
        val flowClass: Class<ResponderFlow> = uncheckedCast(
            sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                flowStartContext.flowClassName,
                ResponderFlow::class.java
            )
        )

        val flowSession = flowSessionFactory.create(
            flowStartContext.statusKey.id, // The ID on a start context is the session ID
            MemberX500Name.parse(flowStartContext.initiatedBy.x500Name),
            initiated = true
        )
        val logic = flowClass.getDeclaredConstructor().newInstance()

        return InitiatedFlow(logic, flowSession)
    }
}

