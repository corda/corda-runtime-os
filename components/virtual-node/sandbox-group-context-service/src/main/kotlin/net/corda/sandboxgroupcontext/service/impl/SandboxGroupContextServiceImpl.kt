package net.corda.sandboxgroupcontext.service.impl

import net.corda.install.InstallService
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.VirtualNodeContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


/**
 * This is the underlying implementation of the [SandboxGroupContextService]
 *
 * Use this service via the mutable and immutable interfaces to create a "virtual node",
 * and retrieve the same instance "later".
 *
 * This is a per-process service, but it must return the "same instance" for a given [VirtualNodeContext]
 * in EVERY process.
 */
class SandboxGroupContextServiceImpl(
    private val sandboxCreationService: SandboxCreationService,
    private val installService: InstallService
) : SandboxGroupContextService, AutoCloseable {
    private val contexts = ConcurrentHashMap<VirtualNodeContext, CloseableSandboxGroupContext>()

    fun remove(virtualNodeContext: VirtualNodeContext) {
        // close actually removes us from the map.
        contexts[virtualNodeContext]?.close()
    }

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        if (contexts.containsKey(virtualNodeContext)) return contexts[virtualNodeContext]!!

        // returns a nullable from a future...
        val cpks = virtualNodeContext.cpkIdentifiers.map { installService.get(it).get()!! }

        val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks, virtualNodeContext.sandboxGroupType.name)

        // Default implementation doesn't do anything on `close()`
        val sandboxGroupContext = SandboxGroupContextImpl(virtualNodeContext, sandboxGroup)

        // Run the caller's initializer.
        val initializerAutoCloseable =
            initializer.initializeSandboxGroupContext(virtualNodeContext.holdingIdentity, sandboxGroupContext)

        // Wrapped SandboxGroupContext, specifically to set closeable and forward on all other calls.

        // Calling close also removes us from the contexts map and unloads the [SandboxGroup]
        val ctx = CloseableSandboxGroupContext(sandboxGroupContext) {
            // These objects might still be in a sandbox, so close them whilst the sandbox is still valid.
            initializerAutoCloseable.close()

            // Finally, remove from contexts map
            val actualSandboxGroupContext = this.contexts.remove(sandboxGroupContext.virtualNodeContext)
            if (actualSandboxGroupContext != null) {
                // And unload the (OSGi) sandbox group
                sandboxCreationService.unloadSandboxGroup(actualSandboxGroupContext.sandboxGroup)
            }
        }

        contexts[virtualNodeContext] = ctx

        return ctx
    }

    /**
     * [MutableSandboxGroupContext] / [SandboxGroupContext] wrapped so that we set [close] now that the user has
     * returned it as part of their [SandboxGroupContextInitializer]
     *
     * We return an instance of this object of type [SandboxGroupContext] to the user once [getOrCreate] is complete.
     */
    private class CloseableSandboxGroupContext(
        private val sandboxGroupContext: SandboxGroupContextImpl,
        private val closeable: AutoCloseable
    ) : MutableSandboxGroupContext, AutoCloseable {
        override fun <T : Any> put(key: String, valueType: KClass<out T>, value: T) =
            sandboxGroupContext.put(key, valueType, value)

        override val virtualNodeContext: VirtualNodeContext
            get() = sandboxGroupContext.virtualNodeContext

        override val sandboxGroup: SandboxGroup
            get() = sandboxGroupContext.sandboxGroup

        override fun <T : Any> get(key: String, valueType: KClass<out T>): T? = sandboxGroupContext.get(key, valueType)

        override fun close() = closeable.close()
    }

    override fun close() {
        contexts.values.forEach(AutoCloseable::close)
    }
}
