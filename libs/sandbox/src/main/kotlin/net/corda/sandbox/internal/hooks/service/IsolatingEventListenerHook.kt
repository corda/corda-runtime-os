package net.corda.sandbox.internal.hooks.service

import net.corda.sandbox.internal.SandboxServiceInternal
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.hooks.service.EventListenerHook
import org.osgi.framework.hooks.service.ListenerHook
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This hook modifies the logic for which bundles receive service events (e.g. registration, modification).
 *
 * We only allow a bundle to receive service events for bundles it has visibility of.
 */
@Component
internal class IsolatingEventListenerHook @Activate constructor(
        @Reference
        private val sandboxService: SandboxServiceInternal) : EventListenerHook {

    override fun event(event: ServiceEvent, listeners: MutableMap<BundleContext, MutableCollection<ListenerHook.ListenerInfo>>) {
        if (!sandboxService.isStarted) return

        val listenersToRemove = listeners.keys.filter { listener ->
            !sandboxService.hasVisibility(listener.bundle, event.serviceReference.bundle)
        }

        listenersToRemove.forEach(listeners::remove)
    }
}
