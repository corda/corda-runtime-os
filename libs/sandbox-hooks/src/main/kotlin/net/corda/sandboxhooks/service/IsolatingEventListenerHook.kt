package net.corda.sandboxhooks.service

import net.corda.sandbox.SandboxContextService
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.hooks.service.EventListenerHook
import org.osgi.framework.hooks.service.ListenerHook

/**
 * This hook modifies the logic for which bundles receive service events (e.g. registration, modification).
 *
 * We only allow a bundle to receive service events for bundles it has visibility of.
 */
internal class IsolatingEventListenerHook(
    private val sandboxService: SandboxContextService
) : EventListenerHook {

    override fun event(event: ServiceEvent, listeners: MutableMap<BundleContext, MutableCollection<ListenerHook.ListenerInfo>>) {
        val listenersToRemove = listeners.keys.filterNot { listener ->
            sandboxService.hasVisibility(listener.bundle, event.serviceReference.bundle)
        }

        listenersToRemove.forEach(listeners::remove)
    }
}
