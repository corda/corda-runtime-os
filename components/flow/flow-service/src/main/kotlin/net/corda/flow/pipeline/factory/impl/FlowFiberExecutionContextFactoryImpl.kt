package net.corda.flow.pipeline.factory.impl

import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowFiberExecutionContextFactory::class])
class FlowFiberExecutionContextFactoryImpl @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider
) : FlowFiberExecutionContextFactory {

    override fun createFiberExecutionContext(
        context: FlowEventContext<Any>
    ): FlowFiberExecutionContext {
        val checkpoint = context.checkpoint
        val sandbox = try {
            flowSandboxService.get(checkpoint.flowStartContext.initiatedBy.toCorda())
        } catch (e: Exception) {
            throw FlowTransientException("Failed to create the sandbox: ${e.message}", context, e)
        }
        return FlowFiberExecutionContext(
            checkpoint,
            sandbox,
            checkpoint.holdingIdentity,
            membershipGroupReaderProvider.getGroupReader(checkpoint.holdingIdentity.toCorda())
        )
    }
}