package net.corda.sandboxhooks.bundle

import net.corda.sandbox.SandboxContextService
import org.osgi.framework.Bundle
import org.osgi.framework.hooks.bundle.CollisionHook
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This hook modifies the logic for identifying collisions (i.e. bundles with the same symbolic name) when installing
 * or updating bundles. We filter out a collision if the colliding bundle is installed in a sandbox.
 *
 * This approach relies on the fact that a CPB's bundles are only added to a sandbox after all the bundles have been
 * installed. Since all bundles are sandboxed, any collisions between unsandboxed bundles must be between the bundles
 * being installed as part of the sandbox currently being prepared.
 *
 * Ideally, we would have knowledge of which bundle is being installed, and we could therefore filter out collisions
 * using a more sophisticated approach (e.g. avoiding collisions if two library bundles are from different CPKs).
 * Unfortunately, [CollisionHook] does not provide this information.
 */
@Component(immediate = true)
internal class IsolatingCollisionBundleHook @Activate constructor(
    @Reference
    private val sandboxService: SandboxContextService
) : CollisionHook {

    // Note that `target` is not the bundle being installed, but the bundle whose context triggered the installation.
    override fun filterCollisions(operationType: Int, target: Bundle, collisionCandidates: MutableCollection<Bundle>) {
        // We filter out any collisions that are installed into sandboxes.
        val candidatesToRemove = collisionCandidates.filterTo(HashSet(), sandboxService::isSandboxed)
        collisionCandidates.removeAll(candidatesToRemove)
    }
}
