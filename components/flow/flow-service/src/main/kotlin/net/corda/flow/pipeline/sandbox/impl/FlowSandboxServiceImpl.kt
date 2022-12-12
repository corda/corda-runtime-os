@file:JvmName("FlowSandboxServiceUtils")
package net.corda.flow.pipeline.sandbox.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.factory.SandboxDependencyInjectorFactory
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.DEPENDENCY_INJECTOR
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.FLOW_PROTOCOL_STORE
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.NON_INJECTABLE_SINGLETONS
import net.corda.flow.pipeline.sessions.FlowProtocolStoreFactory
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.RequireSandboxJSON
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.registerCordappCustomSerializers
import net.corda.sandboxgroupcontext.service.registerCustomCryptography
import net.corda.sandboxgroupcontext.service.registerCustomJsonDeserializers
import net.corda.sandboxgroupcontext.service.registerCustomJsonSerializers
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@RequireSandboxAMQP
@RequireSandboxJSON
@Component(service = [ FlowSandboxService::class ])
class FlowSandboxServiceImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = SandboxDependencyInjectorFactory::class)
    private val dependencyInjectionFactory: SandboxDependencyInjectorFactory,
    @Reference(service = FlowProtocolStoreFactory::class)
    private val flowProtocolStoreFactory: FlowProtocolStoreFactory,
    private val bundleContext: BundleContext
) : FlowSandboxService {

    companion object {
        const val NON_PROTOTYPE_SERVICES = "(!($SERVICE_SCOPE=$SCOPE_PROTOTYPE))"
    }

    override fun get(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext {
        val vNodeInfo = virtualNodeInfoReadService.get(holdingIdentity)
        checkNotNull(vNodeInfo) {
            "Failed to find the virtual node info for holder '${holdingIdentity}' in ${virtualNodeInfoReadService::class.java}"
        }

        val cpiMetadata = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
        checkNotNull(cpiMetadata) { "Failed to find the CPI meta data for '${vNodeInfo.cpiIdentifier}}'" }
        check(cpiMetadata.cpksMetadata.isNotEmpty()) { "No CPKs defined for CPI Meta data id='${cpiMetadata.cpiId}'" }

        val vNodeContext = VirtualNodeContext(
            holdingIdentity,
            cpiMetadata.cpksMetadata.mapTo(linkedSetOf(), CpkMetadata::fileChecksum),
            SandboxGroupType.FLOW,
            null
        )

        if (!sandboxGroupContextComponent.hasCpks(vNodeContext.cpkFileChecksums)) {
            throw IllegalStateException("The sandbox can't find one or more of the CPKs for CPI '${cpiMetadata.cpiId}'")
        }

        val sandboxGroupContext = sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            initialiseSandbox(dependencyInjectionFactory, sandboxGroupContext, cpiMetadata)
        }

        return FlowSandboxGroupContextImpl.fromContext(sandboxGroupContext)
    }

    private fun initialiseSandbox(
        dependencyInjectionFactory: SandboxDependencyInjectorFactory,
        sandboxGroupContext: MutableSandboxGroupContext,
        cpiMetadata: CpiMetadata
    ): AutoCloseable {
        val sandboxGroup = sandboxGroupContext.sandboxGroup
        val customCrypto = sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)

        val injectorService = dependencyInjectionFactory.create(sandboxGroupContext)
        sandboxGroupContext.putObjectByKey(DEPENDENCY_INJECTOR, injectorService)

        val cleanupCordaSingletons = mutableListOf<AutoCloseable>()

        // Identify singleton services outside the sandbox that may need checkpointing.
        // These services should not overlap with the injectable services, which should
        // all have PROTOTYPE scope outside the sandbox.
        val nonInjectableSingletons = getNonInjectableSingletons(cleanupCordaSingletons)
        sandboxGroupContext.putObjectByKey(NON_INJECTABLE_SINGLETONS, nonInjectableSingletons)

        // Build CorDapp serializers
        // Current implementation has unique serializers per CPI
        val customSerializers = sandboxGroupContextComponent.registerCordappCustomSerializers(sandboxGroupContext)

        sandboxGroupContext.putObjectByKey(
            FLOW_PROTOCOL_STORE,
            flowProtocolStoreFactory.create(sandboxGroup, cpiMetadata)
        )

        // User custom serialization support, no exceptions thrown so user code doesn't kill the flow service
        val jsonSerializers = sandboxGroupContextComponent.registerCustomJsonSerializers(sandboxGroupContext)
        val jsonDeserializers = sandboxGroupContextComponent.registerCustomJsonDeserializers(sandboxGroupContext)

        // Instruct all CustomMetadataConsumers to accept their metadata.
        sandboxGroupContextComponent.acceptCustomMetadata(sandboxGroupContext)

        return AutoCloseable {
            jsonDeserializers.close()
            jsonSerializers.close()
            cleanupCordaSingletons.forEach(AutoCloseable::close)
            customSerializers.close()
            injectorService.close()
            customCrypto.close()
        }
    }

    private fun getNonInjectableSingletons(cleanups: MutableList<AutoCloseable>): Set<SingletonSerializeAsToken> {
        // An OSGi singleton component can still register bundle-scoped services, so
        // select the non-prototype ones here. They should all be internal to Corda.
        return bundleContext.getServiceReferences(SingletonSerializeAsToken::class.java, NON_PROTOTYPE_SERVICES)
            .mapNotNullTo(linkedSetOf()) { ref ->
                bundleContext.getService(ref)?.also {
                    cleanups.add(AutoCloseable { bundleContext.ungetService(ref) })
                }
            }
    }
}
