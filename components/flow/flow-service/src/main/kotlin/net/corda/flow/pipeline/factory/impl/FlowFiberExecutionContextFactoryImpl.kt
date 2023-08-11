package net.corda.flow.pipeline.factory.impl

import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.interop.identity.cache.InteropIdentityRegistryService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowFiberExecutionContextFactory::class])
class FlowFiberExecutionContextFactoryImpl @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = InteropIdentityRegistryService::class)
    private val interopIdentityRegistryService: InteropIdentityRegistryService
) : FlowFiberExecutionContextFactory {

    override fun createFiberExecutionContext(
        context: FlowEventContext<Any>
    ): FlowFiberExecutionContext {
        val checkpoint = context.checkpoint
        val sandbox = try {
            flowSandboxService.get(checkpoint.flowStartContext.identity.toCorda(), checkpoint.cpkFileHashes)
        } catch (e: Exception) {
            throw FlowTransientException("Failed to create the sandbox: " +
                    (e.message ?: "No exception message provided."), e)
        }
        return FlowFiberExecutionContext(
            checkpoint,
            sandbox,
            checkpoint.holdingIdentity,
            membershipGroupReaderProvider.getGroupReader(checkpoint.holdingIdentity),
            interopIdentityRegistryService.getVirtualNodeRegistryView(checkpoint.holdingIdentity),
            currentSandboxGroupContext,
            context.mdcProperties,
            context.flowMetrics
        )
    }
}
