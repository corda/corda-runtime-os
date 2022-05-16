package net.corda.flow.pipeline.factory.impl

import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.serialization.checkpoint.CheckpointSerializer
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
        val sandbox = getSandbox(context)
        return FlowFiberExecutionContext(
            sandbox.getDependencyInjector(context),
            checkpoint,
            sandbox.getCheckpointSerializer(context),
            sandbox,
            checkpoint.holdingIdentity,
            membershipGroupReaderProvider.getGroupReader(checkpoint.holdingIdentity.toCorda())
        )
    }

    private fun getSandbox(context: FlowEventContext<Any>): SandboxGroupContext {
        try {
            return flowSandboxService.get(context.checkpoint.flowStartContext.initiatedBy.toCorda())
        } catch (exception: Exception) {
            throw FlowTransientException("Failed to create the sandbox", context, exception)
        }
    }

    private fun SandboxGroupContext.getCheckpointSerializer(context: FlowEventContext<Any>): CheckpointSerializer {
        return getObjectByKey(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER)
            ?: throw FlowFatalException(
                "The ${FlowSandboxContextTypes.CHECKPOINT_SERIALIZER} has not been set on the sandbox",
                context
            )
    }

    private fun SandboxGroupContext.getDependencyInjector(context: FlowEventContext<Any>): SandboxDependencyInjector {
        return getObjectByKey(FlowSandboxContextTypes.DEPENDENCY_INJECTOR)
            ?: throw FlowFatalException(
                "The ${FlowSandboxContextTypes.DEPENDENCY_INJECTOR} has not been set on the sandbox",
                context
            )
    }
}