package net.corda.sandbox.internal.hooks.bundle

import net.corda.sandbox.internal.SandboxServiceInternal
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleEvent
import org.osgi.framework.hooks.bundle.EventHook
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This hook modifies the logic for which bundles receive bundle events (e.g. installation, start).
 *
 * We only allow a bundle to receive bundle events from bundles it has visibility of.
 */
@Component
internal class IsolatingEventHook @Activate constructor(
        @Reference
        private val sandboxService: SandboxServiceInternal) : EventHook {

    override fun event(event: BundleEvent, contexts: MutableCollection<BundleContext>) {
        if (!sandboxService.isStarted) return

        contexts.removeIf { context ->
            !sandboxService.hasVisibility(context.bundle, event.bundle)
        }
    }
}
