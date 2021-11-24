package net.corda.flow.manager.impl

import net.corda.dependency.injection.DependencyInjectionBuilder
import net.corda.dependency.injection.DependencyInjectionBuilderFactory
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.packaging.CPI
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.virtual.node.context.HoldingIdentity
import net.corda.virtual.node.sandboxgroup.MutableSandboxGroupContext
import net.corda.virtual.node.sandboxgroup.SandboxGroupContext
import net.corda.virtual.node.sandboxgroup.SandboxGroupService
import net.corda.virtual.node.sandboxgroup.SandboxGroupType
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowSandboxService::class])
class FlowSandboxServiceImpl(
    @Reference(service = SandboxGroupService::class)
    private val sandboxGroupService: SandboxGroupService,
    @Reference(service = DependencyInjectionBuilderFactory::class)
    private val dependencyInjectionBuilderFactory: DependencyInjectionBuilderFactory,
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory
) {

    fun get(
        holdingIdentity: HoldingIdentity,
        cpi: CPI.Identifier,
    ): SandboxGroupContext {

        val diBuilder = dependencyInjectionBuilderFactory.create()

        return sandboxGroupService.get(
            holdingIdentity,
            cpi,
            SandboxGroupType.FLOW
        ) { _, sandboxGroupContext -> initialiseSandbox(diBuilder, sandboxGroupContext) }
    }

    fun initialiseSandbox(
        dependencyInjectionBuilder: DependencyInjectionBuilder,
        sandboxGroupContext: MutableSandboxGroupContext
    ): AutoCloseable {

        // Register the user defined injectable services from the CPK/CPB and
        // store the injection service in the sandbox context.
        dependencyInjectionBuilder.addSandboxDependencies(sandboxGroupContext)
        val injectionService = dependencyInjectionBuilder.create()
        sandboxGroupContext.put(FlowSandboxContextTypes.DEPENDENCY_INJECTOR, injectionService)

        // Create and configure the checkpoint serializer
        val builder = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxGroupContext.sandboxGroup)
        builder.addSingletonSerializableInstances(injectionService.getRegisteredAsTokenSingletons()) // ?? See comments in the InjectionService class
        sandboxGroupContext.put(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER, injectionService)

        return AutoCloseable { TODO("clean up if required?") }
    }
}