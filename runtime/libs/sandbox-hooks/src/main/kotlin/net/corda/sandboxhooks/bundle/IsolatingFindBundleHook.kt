package net.corda.sandboxhooks.bundle

import net.corda.sandbox.SandboxContextService
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.hooks.bundle.FindHook

/**
 * This hook modifies the logic for how a bundle's context retrieves the list of all bundles.
 *
 * We only allow a bundle to find bundles it has visibility of.
 */
internal class IsolatingFindBundleHook(private val sandboxService: SandboxContextService) : FindHook {

    override fun find(context: BundleContext, bundles: MutableCollection<Bundle>) {
        bundles.removeIf { bundle ->
            !sandboxService.hasVisibility(context.bundle, bundle)
        }
    }
}
