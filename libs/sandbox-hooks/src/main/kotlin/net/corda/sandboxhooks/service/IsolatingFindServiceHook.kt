package net.corda.sandboxhooks.service

import net.corda.sandbox.SandboxContextService
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.hooks.service.FindHook
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This hook modifies the logic for how a bundle's context finds a service reference.
 *
 * We only allow a bundle to find services in bundles it has visibility of.
 */
@Component(immediate = true)
internal class IsolatingFindServiceHook @Activate constructor(
        @Reference
        private val sandboxService: SandboxContextService
) : FindHook {

    override fun find(
        context: BundleContext,
        name: String?,
        filter: String?,
        allServices: Boolean,
        references: MutableCollection<ServiceReference<*>>) {

        references.removeIf { reference ->
            !sandboxService.hasVisibility(context.bundle, reference.bundle)
        }
    }
}