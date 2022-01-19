package net.corda.flow.service

import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.factory.SandboxDependencyInjectionFactory
import net.corda.packaging.CPI
import net.corda.sandbox.service.SandboxService
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowSandboxService::class])
class FlowSandboxServiceImpl @Activate constructor(
    @Reference(service = SandboxService::class)
    private val sandboxService: SandboxService,
    @Reference(service = SandboxDependencyInjectionFactory::class)
    private val dependencyInjectionFactory: SandboxDependencyInjectionFactory,
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory
) : FlowSandboxService {

    override fun get(
        holdingIdentity: HoldingIdentity,
        cpi: CPI.Identifier,
    ): SandboxGroupContext {
        return sandboxService.getOrCreateByCpiIdentifier(
            holdingIdentity, cpi, SandboxGroupType.FLOW
        ) { _, sandboxGroupContext -> initialiseSandbox(dependencyInjectionFactory, sandboxGroupContext) }
    }

    private fun initialiseSandbox(
        dependencyInjectionFactory: SandboxDependencyInjectionFactory,
        sandboxGroupContext: MutableSandboxGroupContext
    ): AutoCloseable {

        val injectionService = dependencyInjectionFactory.create(sandboxGroupContext)
        sandboxGroupContext.putObjectByKey(FlowSandboxContextTypes.DEPENDENCY_INJECTOR, injectionService)

        // Create and configure the checkpoint serializer
        val builder = checkpointSerializerBuilderFactory
            .createCheckpointSerializerBuilder(sandboxGroupContext.sandboxGroup)
        builder.addSingletonSerializableInstances(injectionService.getRegisteredSingletons())
        builder.addSingletonSerializableInstances(setOf(sandboxGroupContext.sandboxGroup))

        sandboxGroupContext.putObjectByKey(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER, builder.build())

        return AutoCloseable { /*TODOs: clean up if required?*/ }
    }
}
