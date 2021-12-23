package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import net.corda.v5.base.util.loggerFor
import org.osgi.framework.Bundle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * An implementation of [Sandbox].
 *
 * @param privateBundles The set of non-public [Bundle]s in this sandbox
 */
internal open class SandboxImpl(
    override val id: UUID,
    final override val publicBundles: Set<Bundle>,
    val privateBundles: Set<Bundle>
) : Sandbox {
    private val logger = loggerFor<SandboxImpl>()

    // The other sandboxes whose services, bundles and events this sandbox can receive.
    // We use the sandboxes' IDs, rather than the sandboxes, to allow unloaded sandboxes to be garbage-collected.
    private val visibleSandboxes = ConcurrentHashMap.newKeySet<UUID>().also { hashMap ->
        // All sandboxes have visibility of themselves.
        hashMap.add(id)
    }

    // All the bundles in the sandbox.
    private val allBundles = privateBundles + publicBundles

    override fun containsBundle(bundle: Bundle) = bundle in allBundles

    override fun hasVisibility(otherSandbox: Sandbox) = otherSandbox.id in visibleSandboxes

    override fun grantVisibility(otherSandboxes: Collection<Sandbox>) {
        visibleSandboxes.addAll(otherSandboxes.map(Sandbox::id))
    }

    override fun loadClass(className: String, bundleName: String): Class<*>? {
        val bundle = allBundles.find { bundle ->
            bundle.symbolicName == bundleName
        } ?: return null

        return try {
            bundle.loadClass(className)
        } catch (e: ClassNotFoundException) {
            return null
        } catch (e: IllegalStateException) {
            throw SandboxException(
                "The bundle ${bundle.symbolicName} in sandbox $id has been uninstalled.", e
            )
        }
    }

    override fun unload() = allBundles.mapNotNull { bundle ->
        try {
            bundle.uninstall()
            null
        } catch (e: IllegalStateException) {
            logger.warn("Bundle ${bundle.symbolicName} is not installed", e)
            null
        } catch (e: Exception) {
            logger.warn("Bundle ${bundle.symbolicName} could not be uninstalled", e)
            bundle
        }
    }

    override fun toString(): String {
        return "Sandbox[ID: $id, PUBLIC: ${publicBundles.joinToString()}, PRIVATE: ${privateBundles.joinToString()}]"
    }
}
