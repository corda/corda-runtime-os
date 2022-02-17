package net.corda.sandboxhooks.bundle

import net.corda.sandbox.SandboxContextService
import org.osgi.framework.hooks.resolver.ResolverHook
import org.osgi.framework.hooks.resolver.ResolverHookFactory
import org.osgi.framework.wiring.BundleCapability
import org.osgi.framework.wiring.BundleRequirement
import org.osgi.framework.wiring.BundleRevision
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This hook modifies the logic for resolving bundles.
 *
 * We only allow a bundle's requirements to be resolved using bundles it has visibility of. We prioritise matches from
 * the bundle's own sandbox, if available.
 *
 * We also allow multiple singleton bundles, provided they do not have visibility of one another.
 */
internal class IsolatingResolverBundleHook(private val sandboxService: SandboxContextService) : ResolverHook {
    // We do not take any action here, and filter the candidates in `filterMatches` instead. Taking an action here
    // causes Felix to create a whitelist in `prepareResolverHooks`, which will be missing the system bundle packages.
    // This is not the behaviour we want.
    override fun filterResolvable(candidates: MutableCollection<BundleRevision>?) {}

    // Bundles with a symbolic name ending in "singleton:=true" will trigger this hook. There cannot normally be
    // multiple instances of the same bundle present in the OSGi framework, but we allow multiple singletons, provided
    // they do not have visibility of one another.
    override fun filterSingletonCollisions(singleton: BundleCapability, collisionCandidates: MutableCollection<BundleCapability>) {
        collisionCandidates.removeIf { candidate ->
            !sandboxService.hasVisibility(singleton.revision.bundle, candidate.revision.bundle)
                    && !sandboxService.hasVisibility(candidate.revision.bundle, singleton.revision.bundle)
        }
    }

    override fun filterMatches(requirement: BundleRequirement, candidates: MutableCollection<BundleCapability>) {
        val candidatesFoundInInitiatingSandbox = mutableListOf<BundleCapability>()

        val candidatesToRemove = candidates.filterNotTo(HashSet()) { candidate ->
            if (sandboxService.areInSameSandbox(requirement.revision.bundle, candidate.revision.bundle)) {
                // Initiating bundle and candidate are in the same sandbox.
                candidatesFoundInInitiatingSandbox.add(candidate)
            }

            sandboxService.hasVisibility(requirement.revision.bundle, candidate.revision.bundle)
        }

        // If any matches were found in the same sandbox, we remove all other matches.
        if (candidatesFoundInInitiatingSandbox.isNotEmpty()) {
            val candidatesNotFoundInOwnSandbox = candidates.filterNotTo(HashSet()) { candidate ->
                candidate in candidatesFoundInInitiatingSandbox
            }
            candidates.removeAll(candidatesNotFoundInOwnSandbox)
        } else {
            candidates.removeAll(candidatesToRemove)
        }
    }

    override fun end() {}
}

/** A [ResolverHookFactory] implementation for creating [IsolatingResolverBundleHook]s. */
@Component(immediate = true)
@Suppress("unused")
internal class IsolatingResolverBundleHookFactory @Activate constructor(
        @Reference
        private val sandboxManagerService: SandboxContextService) : ResolverHookFactory {

    /** Returns an [IsolatingResolverBundleHook]. */
    override fun begin(triggers: Collection<BundleRevision>): ResolverHook {
        return IsolatingResolverBundleHook(sandboxManagerService)
    }
}