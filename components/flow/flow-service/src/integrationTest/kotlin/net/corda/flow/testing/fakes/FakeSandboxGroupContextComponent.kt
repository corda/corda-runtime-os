package net.corda.flow.testing.fakes

import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes.AMQP_P2P_SERIALIZATION_SERVICE
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes.CHECKPOINT_SERIALIZER
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes.DEPENDENCY_INJECTOR
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.libs.packaging.CpkIdentifier
import net.corda.libs.packaging.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [SandboxGroupContextComponent::class, FakeSandboxGroupContextComponent::class])
class FakeSandboxGroupContextComponent : SandboxGroupContextComponent {

    private val availableCpk = mutableSetOf<CpkIdentifier>()

    fun putCpk(cpk: CpkIdentifier){
        availableCpk.add(cpk)
    }

    fun reset(){
        availableCpk.clear()
    }

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        return FakeSandboxGroupContext(virtualNodeContext, FakeSandboxGroup(mapOf()))
    }

    override fun registerMetadataServices(
        sandboxGroupContext: SandboxGroupContext,
        serviceNames: (CpkMetadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean,
        serviceMarkerType: Class<*>
    ): AutoCloseable {
        TODO("Not yet implemented")
    }

    override fun registerCustomCryptography(sandboxGroupContext: SandboxGroupContext): AutoCloseable {
        TODO("Not yet implemented")
    }

    override fun hasCpks(cpkIdentifiers: Set<CpkIdentifier>): Boolean {
        return cpkIdentifiers.any { availableCpk.contains(it) }
    }

    override fun initCache(cacheSize: Long) {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
    }

    override fun stop() {
    }

    class FakeSandboxGroupContext(
        override val virtualNodeContext: VirtualNodeContext,
        override val sandboxGroup: SandboxGroup
    ) :SandboxGroupContext{
        private val serviceMap = mapOf(
            DEPENDENCY_INJECTOR to FakeSandboxDependencyInjector(),
            CHECKPOINT_SERIALIZER to FakeCheckpointSerializer(),
            AMQP_P2P_SERIALIZATION_SERVICE to FakeSerializationService(),
            )

        override fun <T : Any> get(key: String, valueType: Class<out T>): T? {
            return serviceMap[key]?.let(valueType::cast)
        }
    }

    class FakeSandboxDependencyInjector:SandboxDependencyInjector{
        override fun injectServices(flow: Flow<*>) {
        }

        override fun getRegisteredSingletons(): Set<SingletonSerializeAsToken> {
            return setOf()
        }

        override fun close() {
        }
    }

    class FakeCheckpointSerializer:CheckpointSerializer{
        override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any> serialize(obj: T): ByteArray {
            TODO("Not yet implemented")
        }
    }

    class FakeSerializationService:SerializationService{
        override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
            TODO("Not yet implemented")
        }
    }

    class FakeSandboxGroup(override val metadata: Map<Bundle, CpkMetadata>) :SandboxGroup{
        override fun loadClassFromMainBundles(className: String): Class<*> {
            TODO("Not yet implemented")
        }

        override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> {
            TODO("Not yet implemented")
        }

        override fun getStaticTag(klass: Class<*>): String {
            TODO("Not yet implemented")
        }

        override fun getEvolvableTag(klass: Class<*>): String {
            TODO("Not yet implemented")
        }

        override fun getClass(className: String, serialisedClassTag: String): Class<*> {
            TODO("Not yet implemented")
        }
    }
}