package net.corda.flow.testing.fakes

import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [SandboxGroupContextComponent::class, FakeSandboxGroupContextComponent::class])
class FakeSandboxGroupContextComponent : SandboxGroupContextComponent {

    private val availableCpk = mutableSetOf<SecureHash>()
    private var initiatingToInitiatedFlowsMap: Map<String, Pair<String, String>> = mapOf()

    fun putCpk(cpkFileChecksum: SecureHash) {
        availableCpk.add(cpkFileChecksum)
    }

    fun reset() {
        availableCpk.clear()
    }

    fun initiatingToInitiatedFlowPair(protocolName: String, initiatingFlowClassName: String, initiatedFlowClassName: String) {
        initiatingToInitiatedFlowsMap = mapOf(protocolName to Pair(initiatingFlowClassName, initiatedFlowClassName))
    }

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        return FakeSandboxGroupContext(virtualNodeContext, FakeSandboxGroup(UUID.randomUUID(), mapOf()), initiatingToInitiatedFlowsMap)
    }

    override fun registerMetadataServices(
        sandboxGroupContext: SandboxGroupContext,
        serviceNames: (CpkMetadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean,
        serviceMarkerType: Class<*>
    ): AutoCloseable {
        TODO("Not yet implemented")
    }

    override fun acceptCustomMetadata(sandboxGroupContext: MutableSandboxGroupContext) {
        TODO("Not yet implemented")
    }

    override fun hasCpks(cpkChecksums: Set<SecureHash>): Boolean {
        return cpkChecksums.any { availableCpk.contains(it) }
    }

    override fun initCache(capacity: Long) {
        TODO("Not yet implemented")
    }

    override fun flushCache(): CompletableFuture<*> {
        TODO("Not yet implemented")
    }

    override fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
    }

    override fun stop() {
    }

    override fun close() {
    }

    class FakeSandboxGroupContext(
        override val virtualNodeContext: VirtualNodeContext,
        override val sandboxGroup: SandboxGroup,
        private val initiatingToInitiatedFlowsMap: Map<String, Pair<String, String>>
    ) :SandboxGroupContext{
        private val cache = mapOf(
            FlowSandboxGroupContextImpl.DEPENDENCY_INJECTOR to FakeSandboxDependencyInjector(),
            FlowSandboxGroupContextImpl.CHECKPOINT_SERIALIZER to FakeCheckpointSerializer(),
            RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE to FakeSerializationService(),
            FlowSandboxGroupContextImpl.FLOW_PROTOCOL_STORE to makeProtocolStore()
        )

        override val completion: CompletableFuture<Boolean> = CompletableFuture()

        override fun <T : Any> get(key: String, valueType: Class<out T>): T? {
            return cache[key]?.let(valueType::cast)
        }

        private fun makeProtocolStore() : FakeFlowProtocolStore {
            val initiatingMap = initiatingToInitiatedFlowsMap.map {
                Pair(it.value.first, Pair(it.key, listOf(1)))
            }.toMap()
            val initiatorMap = initiatingToInitiatedFlowsMap.map {
                Pair(it.key, it.value.first)
            }.toMap()
            val responderMap = initiatingToInitiatedFlowsMap.map {
                Pair(it.key, it.value.second)
            }.toMap()
            return FakeFlowProtocolStore(initiatingMap, initiatorMap, responderMap)
        }
    }

    class FakeSandboxDependencyInjector : SandboxDependencyInjector {
        override fun injectServices(flow: Flow) {
        }

        override fun getRegisteredServices(): Set<SingletonSerializeAsToken> {
            return emptySet()
        }

        override fun close() {
        }
    }

    class FakeCheckpointSerializer : CheckpointSerializer {
        override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any> serialize(obj: T): ByteArray {
            TODO("Not yet implemented")
        }
    }

    class FakeSerializationService : SerializationService {
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

    class FakeSandboxGroup(override val id: UUID, override val metadata: Map<Bundle, CpkMetadata>) : SandboxGroup {
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

        override fun loadClassFromPublicBundles(className: String): Class<*>? {
            TODO("Not yet implemented")
        }
    }

    class FakeFlowProtocolStore(
        private val protocolForInitiator: Map<String, Pair<String, List<Int>>>,
        private val initiatorForProtocol: Map<String, String>,
        private val responderForProtocol: Map<String, String>
    ) : FlowProtocolStore {
        override fun initiatorForProtocol(protocolName: String, supportedVersions: Collection<Int>): String {
            return initiatorForProtocol[protocolName] ?: throw IllegalArgumentException("No initiator configured for $protocolName")
        }

        override fun responderForProtocol(protocolName: String, supportedVersions: Collection<Int>, context: FlowEventContext<*>): String {
            return responderForProtocol[protocolName] ?: throw IllegalArgumentException("No responder configured for $protocolName")
        }

        override fun protocolsForInitiator(initiator: String, context: FlowEventContext<*>): Pair<String, List<Int>> {
            return protocolForInitiator[initiator] ?: throw IllegalArgumentException("No protocol configured for $initiator")
        }
    }
}
