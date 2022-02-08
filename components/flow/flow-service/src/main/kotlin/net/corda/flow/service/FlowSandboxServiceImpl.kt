@file:JvmName("FlowSandboxServiceUtils")
package net.corda.flow.service

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.manager.FlowSandboxContextTypes.CHECKPOINT_SERIALIZER
import net.corda.flow.manager.FlowSandboxContextTypes.DEPENDENCY_INJECTOR
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.factory.SandboxDependencyInjectorFactory
import net.corda.packaging.CPK
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.application.services.CordaService
import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.manager.api.RuntimeRegistration
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.lang.reflect.Modifier

@Suppress("LongParameterList", "unused")
@Component(service = [FlowSandboxService::class])
class FlowSandboxServiceImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = SandboxDependencyInjectorFactory::class)
    private val dependencyInjectionFactory: SandboxDependencyInjectorFactory,
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory,
    @Reference
    private val registrar: RuntimeRegistration
) : FlowSandboxService {
    private val logger = loggerFor<FlowSandboxServiceImpl>()

    override fun get(
        holdingIdentity: HoldingIdentity
    ): SandboxGroupContext {

        val vNodeInfo = virtualNodeInfoReadService.get(holdingIdentity)
        checkNotNull(vNodeInfo) { "Failed to find the virtual node info for holder '${holdingIdentity}}'" }

        val cpiMeta = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
        checkNotNull(cpiMeta) { "Failed to find the CPI meta data for '${vNodeInfo.cpiIdentifier}}'" }
        check(cpiMeta.cpks.isNotEmpty()) { "No CPKs defined for CPI Meta data id='${cpiMeta.id}}'" }

        val vNodeContext = VirtualNodeContext(
            holdingIdentity,
            cpiMeta.cpks.mapTo(LinkedHashSet(), CPK.Metadata::id),
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )

        if (!sandboxGroupContextComponent.hasCpks(vNodeContext.cpkIdentifiers)) {
            throw IllegalStateException("The sandbox can't find one or more of the CPKs for CPI '${cpiMeta.id}'")
        }

        return sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            initialiseSandbox(dependencyInjectionFactory, sandboxGroupContext)
        }
    }

    private fun initialiseSandbox(
        dependencyInjectionFactory: SandboxDependencyInjectorFactory,
        sandboxGroupContext: MutableSandboxGroupContext
    ): AutoCloseable {
        val sandboxGroup = sandboxGroupContext.sandboxGroup
        registrar.register(sandboxGroup)

        // Declare services implemented within this sandbox group for CordaInject support.
        val sandboxServices = sandboxGroupContextComponent.registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.services },
            isMetadataService = Class<*>::isInjectableCordaService
        )

        val injectorService = dependencyInjectionFactory.create(sandboxGroupContext)
        sandboxGroupContext.putObjectByKey(DEPENDENCY_INJECTOR, injectorService)

        // Create and configure the checkpoint serializer
        val checkpointSerializer = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxGroup).let { builder ->
            builder.addSingletonSerializableInstances(injectorService.getRegisteredSingletons())
            builder.addSingletonSerializableInstances(setOf(sandboxGroup))
            builder.build()
        }
        sandboxGroupContext.putObjectByKey(CHECKPOINT_SERIALIZER, checkpointSerializer)

        return AutoCloseable {
            injectorService.close()
            sandboxServices.close()
            registrar.unregister(sandboxGroup)
        }
    }
}

private const val PUBLIC_ABSTRACT = Modifier.PUBLIC or Modifier.ABSTRACT
private val Class<*>.isInjectableCordaService: Boolean get() {
    // This class should:
    // - implement CordaService
    // - implement either CordaFlowInjectable or CordaServiceInjectable
    // - be both public and non-abstract.
    return CordaService::class.java.isAssignableFrom(this)
        && (CordaFlowInjectable::class.java.isAssignableFrom(this) || CordaServiceInjectable::class.java.isAssignableFrom(this))
        && (modifiers and PUBLIC_ABSTRACT == Modifier.PUBLIC)
}
