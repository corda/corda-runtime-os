@file:JvmName("FlowSandboxServiceUtils")
package net.corda.flow.service

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.manager.FlowSandboxContextTypes.CHECKPOINT_SERIALIZER
import net.corda.flow.manager.FlowSandboxContextTypes.DEPENDENCY_INJECTOR
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.factory.SandboxDependencyInjectorFactory
import net.corda.libs.packaging.CpkMetadata
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
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SERVICE_SCOPE
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
    private val bundleContext: BundleContext
) : FlowSandboxService {
    private companion object {
        private const val NON_PROTOTYPE_SERVICES = "(!($SERVICE_SCOPE=$SCOPE_PROTOTYPE))"
    }

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
            cpiMeta.cpks.mapTo(LinkedHashSet(), CpkMetadata::id),
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
        val customCrypto = sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)

        // Declare services implemented within this sandbox group for CordaInject support.
        val sandboxServices = sandboxGroupContextComponent.registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.services },
            isMetadataService = Class<*>::isInjectableCordaService
        )

        val injectorService = dependencyInjectionFactory.create(sandboxGroupContext)
        sandboxGroupContext.putObjectByKey(DEPENDENCY_INJECTOR, injectorService)

        // Identify singleton services outside the sandbox that may need checkpointing.
        // These services should not overlap with the injectable services, which should
        // all have PROTOTYPE scope outside the sandbox.
        val cleanupCordaSingletons = mutableListOf<AutoCloseable>()
        val nonInjectableSingletons = getNonInjectableSingletons(cleanupCordaSingletons)

        // Create and configure the checkpoint serializer
        val checkpointSerializer = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxGroup).let { builder ->
            builder.addSingletonSerializableInstances(injectorService.getRegisteredSingletons())
            builder.addSingletonSerializableInstances(nonInjectableSingletons)
            builder.addSingletonSerializableInstances(setOf(sandboxGroup))
            builder.build()
        }
        sandboxGroupContext.putObjectByKey(CHECKPOINT_SERIALIZER, checkpointSerializer)

        return AutoCloseable {
            cleanupCordaSingletons.forEach(AutoCloseable::close)
            injectorService.close()
            sandboxServices.close()
            customCrypto.close()
        }
    }

    private fun getNonInjectableSingletons(cleanups: MutableList<AutoCloseable>): Set<SingletonSerializeAsToken> {
        // An OSGi singleton component can still register bundle-scoped services, so
        // select the non-prototype ones here. They should all be internal to Corda.
        return bundleContext.getServiceReferences(SingletonSerializeAsToken::class.java, NON_PROTOTYPE_SERVICES)
            .mapNotNullTo(HashSet()) { ref ->
                bundleContext.getService(ref)?.also {
                    cleanups.add(AutoCloseable { bundleContext.ungetService(ref) })
                }
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
