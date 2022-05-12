package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.messaging.FlowSession
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

    override fun createInitiatedFlow(flowStartContext: FlowStartContext, sandboxGroupContext: SandboxGroupContext): Flow<*> {
        val flowClass: Class<Flow<*>> = uncheckedCast(
            sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                flowStartContext.flowClassName,
                Flow::class.java
            )
        )

        val flowSession = flowSessionFactory.create(
            flowStartContext.statusKey.id, // The ID on a start context is the session ID
            MemberX500Name.parse(flowStartContext.initiatedBy.x500Name),
            initiated = true
        )

        return flowClass
            .getDeclaredConstructor(FlowSession::class.java)
            .newInstance(flowSession)
    }
}

