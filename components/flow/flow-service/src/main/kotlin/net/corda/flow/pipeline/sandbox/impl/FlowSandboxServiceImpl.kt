package net.corda.flow.pipeline.sandbox.impl

import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.DEPENDENCY_INJECTOR
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.FLOW_PROTOCOL_STORE
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.NON_INJECTABLE_SINGLETONS
import net.corda.flow.pipeline.sessions.FlowProtocolStoreFactory
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.RequireSandboxJSON
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.factory.SandboxDependencyInjectorFactory
import net.corda.sandboxgroupcontext.service.registerCordappCustomSerializers
import net.corda.sandboxgroupcontext.service.registerCustomCryptography
import net.corda.sandboxgroupcontext.service.registerCustomJsonDeserializers
import net.corda.sandboxgroupcontext.service.registerCustomJsonSerializers
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@RequireSandboxAMQP
@RequireSandboxJSON
@Component(service = [ FlowSandboxService::class ])
class FlowSandboxServiceImpl @Activate constructor(
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = SandboxDependencyInjectorFactory::class)
    private val dependencyInjectionFactory: SandboxDependencyInjectorFactory,
    @Reference(service = FlowProtocolStoreFactory::class)
    private val flowProtocolStoreFactory: FlowProtocolStoreFactory,
    private val bundleContext: BundleContext
) : FlowSandboxService {

    private companion object {
        private const val NON_PROTOTYPE_SERVICES = "(!($SERVICE_SCOPE=$SCOPE_PROTOTYPE))"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun get(holdingIdentity: HoldingIdentity, cpkFileHashes: Set<SecureHash>): FlowSandboxGroupContext {
        val vNodeContext = VirtualNodeContext(
            holdingIdentity,
            cpkFileHashes,
            SandboxGroupType.FLOW,
            null
        )

        if (!sandboxGroupContextComponent.hasCpks(vNodeContext.cpkFileChecksums)) {
            throw IllegalStateException("The sandbox can't find one or more of the CPKs $cpkFileHashes ")
        }

        val sandboxGroupContext = sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            initialiseSandbox(sandboxGroupContext)
        }

        return FlowSandboxGroupContextImpl.fromContext(sandboxGroupContext)
    }

    private fun initialiseSandbox(
        sandboxGroupContext: MutableSandboxGroupContext,
    ): AutoCloseable {
        val sandboxGroup = sandboxGroupContext.sandboxGroup
        logger.info("Before registerCustomCryptography")
        val customCrypto = sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)

        logger.info("Before injectorService")
        val injectorService = FlowSandboxDependencyInjectorImpl(dependencyInjectionFactory.create(sandboxGroupContext))
        sandboxGroupContext.putObjectByKey(DEPENDENCY_INJECTOR, injectorService)

        val cleanupCordaSingletons = mutableListOf<AutoCloseable>()

        // Identify singleton services outside the sandbox that may need checkpointing.
        // These services should not overlap with the injectable services, which should
        // all have PROTOTYPE scope outside the sandbox.
        logger.info("Before nonInjectableSingletons")
        val nonInjectableSingletons = getNonInjectableSingletons(cleanupCordaSingletons)
        sandboxGroupContext.putObjectByKey(NON_INJECTABLE_SINGLETONS, nonInjectableSingletons)

        // Build CorDapp serializers
        // Current implementation has unique serializers per CPI
        logger.info("Before registerCordappCustomSerializers")
        val customSerializers = sandboxGroupContextComponent.registerCordappCustomSerializers(sandboxGroupContext)

        logger.info("Before sandboxGroupContext putObjectByKey")
        sandboxGroupContext.putObjectByKey(
            FLOW_PROTOCOL_STORE,
            flowProtocolStoreFactory.create(sandboxGroup)
        )

        // User custom serialization support, no exceptions thrown so user code doesn't kill the flow service
        logger.info("Before jsonSerializers")
        val jsonSerializers = sandboxGroupContextComponent.registerCustomJsonSerializers(sandboxGroupContext)
        logger.info("Before jsonDeserializers")
        val jsonDeserializers = sandboxGroupContextComponent.registerCustomJsonDeserializers(sandboxGroupContext)

        // Instruct all CustomMetadataConsumers to accept their metadata.

        sandboxGroupContextComponent.acceptCustomMetadata(sandboxGroupContext)
        logger.info("Before returning")
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
