@file:JvmName("FlowSandboxServiceUtils")
package net.corda.flow.pipeline.sandbox.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.factory.SandboxDependencyInjectorFactory
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxServiceImpl.Companion.INTERNAL_CUSTOM_SERIALIZERS
import net.corda.flow.pipeline.sessions.FlowProtocolStoreFactory
import net.corda.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC

@Suppress("LongParameterList")
@Component(
    service = [FlowSandboxService::class],
    reference = [
        Reference(
            name = INTERNAL_CUSTOM_SERIALIZERS,
            service = InternalCustomSerializer::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        )
    ]
)
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
    @Reference(service = FlowProtocolStoreFactory::class)
    private val flowProtocolStoreFactory: FlowProtocolStoreFactory,
    private val componentContext: ComponentContext
) : FlowSandboxService {

    companion object {
        const val INTERNAL_CUSTOM_SERIALIZERS = "internalCustomSerializers"
        private const val NON_PROTOTYPE_SERVICES = "(!($SERVICE_SCOPE=$SCOPE_PROTOTYPE))"

        private fun <T> ComponentContext.fetchServices(refName: String): List<T> {
            @Suppress("unchecked_cast")
            return (locateServices(refName) as? Array<T>)?.toList() ?: emptyList()
        }
    }

    private val log = loggerFor<FlowSandboxServiceImpl>()

    private val internalCustomSerializers
        get() = componentContext.fetchServices<InternalCustomSerializer<out Any>>(INTERNAL_CUSTOM_SERIALIZERS)

    override fun get(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext {

        val vNodeInfo = virtualNodeInfoReadService.get(holdingIdentity)
        checkNotNull(vNodeInfo) { "Failed to find the virtual node info for holder '${holdingIdentity}}'" }

        val cpiMetadata = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
        checkNotNull(cpiMetadata) { "Failed to find the CPI meta data for '${vNodeInfo.cpiIdentifier}}'" }
        check(cpiMetadata.cpksMetadata.isNotEmpty()) { "No CPKs defined for CPI Meta data id='${cpiMetadata.cpiId}}'" }

        val vNodeContext = VirtualNodeContext(
            holdingIdentity,
            cpiMetadata.cpksMetadata.mapTo(LinkedHashSet(), CpkMetadata::cpkId),
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )

        if (!sandboxGroupContextComponent.hasCpks(vNodeContext.cpkIdentifiers)) {
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
        sandboxGroupContext.putObjectByKey(FlowSandboxGroupContextImpl.DEPENDENCY_INJECTOR, injectorService)

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
        sandboxGroupContext.putObjectByKey(FlowSandboxGroupContextImpl.CHECKPOINT_SERIALIZER, checkpointSerializer)

        sandboxGroupContext.putAMQPSerializationEnvironment(cpiMetadata)

        sandboxGroupContext.putObjectByKey(
            FlowSandboxGroupContextImpl.FLOW_PROTOCOL_STORE,
            flowProtocolStoreFactory.create(sandboxGroup, cpiMetadata)
        )

        return AutoCloseable {
            cleanupCordaSingletons.forEach(AutoCloseable::close)
            injectorService.close()
            customCrypto.close()
        }
    }

    private fun getNonInjectableSingletons(cleanups: MutableList<AutoCloseable>): Set<SingletonSerializeAsToken> {
        val bundleContext = componentContext.bundleContext
        // An OSGi singleton component can still register bundle-scoped services, so
        // select the non-prototype ones here. They should all be internal to Corda.
        return bundleContext.getServiceReferences(SingletonSerializeAsToken::class.java, NON_PROTOTYPE_SERVICES)
            .mapNotNullTo(HashSet()) { ref ->
                bundleContext.getService(ref)?.also {
                    cleanups.add(AutoCloseable { bundleContext.ungetService(ref) })
                }
            }
    }

    private fun MutableSandboxGroupContext.putAMQPSerializationEnvironment(cpiMetadata: CpiMetadata) {
        val factory = SerializerFactoryBuilder.build(sandboxGroup)
        for (customSerializer in internalCustomSerializers) {
            log.info("Registering internal serializer {}", customSerializer.javaClass.name)
            factory.register(customSerializer, factory)
        }
        // Build CorDapp serializers
        // Current implementation has unique serializers per CPI
        val cordappCustomSerializers = buildCorDappSerializers(
            sandboxGroup,
            serializerClassNames = cpiMetadata.cpksMetadata.flatMap { it.cordappManifest.serializers }.toSet()
        )
        // Register CorDapp serializers
        for (customSerializer in cordappCustomSerializers) {
            log.info("Registering CorDapp serializer {}", customSerializer.javaClass.name)
            factory.registerExternal(customSerializer, factory)
        }

        val serializationOutput = SerializationOutput(factory)
        val deserializationInput = DeserializationInput(factory)

        val p2pSerializationService = SerializationServiceImpl(
            serializationOutput,
            deserializationInput,
            AMQP_P2P_CONTEXT.withSandboxGroup(sandboxGroup)
        )

        putObjectByKey(FlowSandboxGroupContextImpl.AMQP_P2P_SERIALIZATION_SERVICE, p2pSerializationService)
    }

    private fun buildCorDappSerializers(
        sandboxGroup: SandboxGroup,
        serializerClassNames: Set<String>
    ): List<SerializationCustomSerializer<*, *>> {
        return serializerClassNames.map { serializerClassName ->
            sandboxGroup.loadClassFromMainBundles(
                serializerClassName,
                SerializationCustomSerializer::class.java
            ).getConstructor().newInstance()
        }
    }
}
