package net.corda.sandbox.internal.hooks.bundle

import net.corda.sandbox.internal.SandboxServiceInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.hooks.bundle.FindHook
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This hook modifies the logic for how a bundle's context retrieves the list of all bundles.
 *
 * We only allow a bundle to find bundles it has visibility of.
 */
@Component
internal class IsolatingFindBundleHook @Activate constructor(
        @Reference
        private val sandboxService: SandboxServiceInternal
) : FindHook {

    override fun find(context: BundleContext, bundles: MutableCollection<Bundle>) {
        bundles.removeIf { bundle ->
            !sandboxService.hasVisibility(context.bundle, bundle)
        }
    }
}