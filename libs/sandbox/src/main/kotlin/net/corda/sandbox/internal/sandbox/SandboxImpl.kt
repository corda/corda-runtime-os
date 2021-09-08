package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.osgi.framework.Bundle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * An implementation of [SandboxInternal].
 *
 * @param bundleUtils The [BundleUtils] that all OSGi activity is delegated to for testing purposes
 * @param privateBundles The set of non-public [Bundle]s in this sandbox
 */
internal open class SandboxImpl(
    private val bundleUtils: BundleUtils,
    override val id: UUID,
    final override val publicBundles: Set<Bundle>,
    private val privateBundles: Set<Bundle>
) : SandboxInternal {
    // The other sandboxes whose services, bundles and events this sandbox can receive.
    private val visibleSandboxes = ConcurrentHashMap.newKeySet<Sandbox>().also {
        it.add(this)
    }

    // All the bundles in the sandbox.
    private val allBundles = privateBundles + publicBundles

    override fun containsBundle(bundle: Bundle) = bundle in allBundles

    override fun containsClass(klass: Class<*>) = bundleUtils.getBundle(klass) in allBundles

    override fun hasVisibility(otherSandbox: Sandbox) = otherSandbox in visibleSandboxes

    override fun grantVisibility(otherSandbox: Sandbox) {
        visibleSandboxes.add(otherSandbox)
    }

    override fun getBundle(bundleName: String) = (publicBundles + privateBundles).find { bundle ->
        bundle.symbolicName == bundleName
    }

    override fun loadClass(className: String, bundleName: String): Class<*>? {
        val bundle = getBundle(bundleName) ?: return null

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
}