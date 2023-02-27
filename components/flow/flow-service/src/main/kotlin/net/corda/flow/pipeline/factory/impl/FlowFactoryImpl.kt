package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.ClientRequestBodyImpl
import net.corda.flow.fiber.ClientStartedFlow
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowFactory::class])
@Suppress("Unused")
class FlowFactoryImpl @Activate constructor(
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory,
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowFactory {

    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): FlowLogicAndArgs {
        return try {
            val flowClass = sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                startFlowEvent.startContext.flowClassName,
                ClientStartableFlow::class.java
            )
            val logic = flowClass.getDeclaredConstructor().newInstance()
            val args = ClientRequestBodyImpl(flowFiberService)

            ClientStartedFlow(logic, args)
        } catch (e: Exception) {
            throw FlowFatalException(
                "Could not create ${startFlowEvent.startContext.flowClassName} for " +
                        "virtual node ${startFlowEvent.startContext.identity}: ${e.message}", e
            )
        }
    }

    override fun createInitiatedFlow(
        flowStartContext: FlowStartContext,
        sandboxGroupContext: SandboxGroupContext,
        contextProperties: Map<String, String>
    ): FlowLogicAndArgs {
        return try {
            val flowClass = sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                flowStartContext.flowClassName,
                ResponderFlow::class.java
            )

            val flowSession = flowSessionFactory.createInitiatedFlowSession(
                flowStartContext.statusKey.id, // The ID on a start context is the session ID
                MemberX500Name.parse(flowStartContext.initiatedBy.x500Name),
                contextProperties
            )
            val logic = flowClass.getDeclaredConstructor().newInstance()

            InitiatedFlow(logic, flowSession)
        } catch (e: Exception) {
            throw FlowFatalException(
                "Could not create initiated flow ${flowStartContext.flowClassName} for " +
                        "virtual node ${flowStartContext.identity}: ${e.message}", e
            )
        }
    }
}

