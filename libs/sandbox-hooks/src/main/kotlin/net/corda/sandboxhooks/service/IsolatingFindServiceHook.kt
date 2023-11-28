package net.corda.sandboxhooks.service

import net.corda.sandbox.SandboxContextService
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.hooks.service.FindHook

/**
 * This hook modifies the logic for how a bundle's context finds a service reference.
 *
 * We only allow a bundle to find services in bundles it has visibility of.
 */
internal class IsolatingFindServiceHook(
    private val sandboxService: SandboxContextService
) : FindHook {

    override fun find(
        context: BundleContext,
        name: String?,
        filter: String?,
        allServices: Boolean,
        references: MutableCollection<ServiceReference<*>>
    ) {
        references.removeIf { reference ->
            !sandboxService.hasVisibility(context.bundle, reference.bundle)
        }
    }
}
