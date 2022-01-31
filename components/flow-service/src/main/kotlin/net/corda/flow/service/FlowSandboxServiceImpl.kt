package net.corda.flow.service

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.factory.SandboxDependencyInjectionFactory
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowSandboxService::class])
class FlowSandboxServiceImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = SandboxDependencyInjectionFactory::class)
    private val dependencyInjectionFactory: SandboxDependencyInjectionFactory,
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory
) : FlowSandboxService {

    override fun get(
        holdingIdentity: HoldingIdentity
    ): SandboxGroupContext {

        val vNodeInfo = virtualNodeInfoReadService.get(holdingIdentity)
        checkNotNull(vNodeInfo) { "Failed to find the virtual node info for holder '${holdingIdentity}}'" }

        val cpiMeta = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
        checkNotNull(cpiMeta) { "Failed to find the CPI meta data for '${vNodeInfo.cpiIdentifier}}'" }
        checkNotNull(cpiMeta.cpks) { "No CPKs defined for CPI Meta data id='${cpiMeta.id}}'" }

        val vNodeContext = VirtualNodeContext(
            holdingIdentity,
            cpiMeta.cpks.map { it.id }.toSet(),
            SandboxGroupType.FLOW
        )

        if (!sandboxGroupContextComponent.hasCpks(vNodeContext.cpkIdentifiers)) {
            throw IllegalStateException("The sandbox can't find one or more of the CPKs for CPI '${cpiMeta.id}'")
        }

        return sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            initialiseSandbox(dependencyInjectionFactory, sandboxGroupContext)
        }
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
