package net.corda.sandboxhooks.register

import net.corda.kotlin.reflect.heap.KotlinHeapFixer
import net.corda.sandbox.SandboxContextService
import net.corda.sandboxhooks.bundle.IsolatingCollisionBundleHook
import net.corda.sandboxhooks.bundle.IsolatingEventHook
import net.corda.sandboxhooks.bundle.IsolatingFindBundleHook
import net.corda.sandboxhooks.bundle.IsolatingResolverBundleHookFactory
import net.corda.sandboxhooks.service.IsolatingEventListenerHook
import net.corda.sandboxhooks.service.IsolatingFindServiceHook
import org.osgi.framework.BundleContext
import org.osgi.framework.hooks.bundle.CollisionHook
import org.osgi.framework.hooks.bundle.EventHook
import org.osgi.framework.hooks.resolver.ResolverHookFactory
import org.osgi.framework.hooks.service.EventListenerHook
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

/**
 * Register our sandbox hooks with the OSGi framework.
 * Creating these hooks as individual OSGi components
 * was causing circular dependency errors within the SCR
 * as our applications started up. Avoid this problem by
 * registering our hooks directly as OSGi services.
 */
@Suppress("unused")
@Component(immediate = true, service = [])
class Registration @Activate constructor(
    @Reference
    sandboxService: SandboxContextService,
    bundleContext: BundleContext
) {
    init {
        // Purge Kotlin reflection of WeakReference objects belonging to unloaded sandboxes.
        // This is a temporary workaround until Kotlin handles this itself.
        // See https://youtrack.jetbrains.com/issue/KT-56619.
        KotlinHeapFixer.start()

        with(bundleContext) {
            registerService(EventListenerHook::class.java, IsolatingEventListenerHook(sandboxService), null)
            registerService(org.osgi.framework.hooks.service.FindHook::class.java, IsolatingFindServiceHook(sandboxService), null)
            registerService(CollisionHook::class.java, IsolatingCollisionBundleHook(sandboxService), null)
            registerService(EventHook::class.java, IsolatingEventHook(sandboxService), null)
            registerService(org.osgi.framework.hooks.bundle.FindHook::class.java, IsolatingFindBundleHook(sandboxService),  null)
            registerService(ResolverHookFactory::class.java, IsolatingResolverBundleHookFactory(sandboxService), null)
        }
    }

    @Deactivate
    fun shutdown() {
        KotlinHeapFixer.stop()
    }
}
