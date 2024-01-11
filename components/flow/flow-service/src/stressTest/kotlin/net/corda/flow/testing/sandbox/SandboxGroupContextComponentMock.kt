package net.corda.flow.testing.sandbox

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.*
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.sandboxgroupcontext.service.EvictionListener
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.time.Duration
import java.util.concurrent.CompletableFuture

@ServiceRanking(Int.MAX_VALUE)
@Component(
    service = [SandboxGroupContextComponent::class, SandboxGroupContextComponentMock::class, CacheEviction::class])
class SandboxGroupContextComponentMock : SandboxGroupContextComponent {
    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun resizeCache(type: SandboxGroupType, capacity: Long) {
        TODO("Not yet implemented")
    }

    override fun flushCache(): CompletableFuture<*> {
        TODO("Not yet implemented")
    }

    override fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? {
        TODO("Not yet implemented")
    }

    override fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean {
        TODO("Not yet implemented")
    }

    override fun addEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
    }

    override fun stop() {
    }
}