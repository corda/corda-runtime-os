package net.corda.sandbox.internal.hooks.bundle

import net.corda.sandbox.internal.SandboxServiceInternal
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.hooks.bundle.FindHook
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This hook modifies the logic for how a bundle's context retrieves the list of all bundles.
 *
 * We only allow a bundle to find bundles it has visibility of. The exception is bundles in the platform sandbox, which
 * are allowed to see all bundles.
 */
@Component
internal class IsolatingFindBundleHook @Activate constructor(
        @Reference
        private val sandboxService: SandboxServiceInternal) : FindHook {

    override fun find(context: BundleContext, bundles: MutableCollection<Bundle>) {
        // We allow the platform sandbox to see all CorDapp bundles. This is required for `OSGiAggregateClassloader`.
        val currentSandbox = sandboxService.getSandbox(context.bundle)
        if (currentSandbox != null && sandboxService.isPlatformSandbox(currentSandbox)) {
            return
        }

        bundles.removeIf { bundle ->
            !sandboxService.hasVisibility(context.bundle, bundle)
        }
    }
}