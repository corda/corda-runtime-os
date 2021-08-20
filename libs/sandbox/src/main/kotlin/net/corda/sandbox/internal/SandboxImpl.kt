package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * An implementation of the [Sandbox] interface.
 *
 * @param cpk The CPK this sandbox is constructed from, or null if it's a platform sandbox
 * @param cordappBundle The CPK's CorDapp bundle, or null if it's a platform sandbox
 * @param otherBundles All other bundles in the sandbox
 */
internal class SandboxImpl(
        private val bundleUtils: BundleUtils,
        override val id: UUID,
        override val cpk: Cpk.Expanded?,
        private val cordappBundle: Bundle?,
        otherBundles: Set<Bundle>
) : SandboxInternal {

    private val bundles = (otherBundles + cordappBundle).filterNotNull()

    // The other sandboxes whose services, bundles and events this sandbox can receive.
    private val visibleSandboxes = ConcurrentHashMap.newKeySet<Sandbox>()

    override fun loadClass(className: String) = try {
        cordappBundle?.loadClass(className) ?: throw SandboxException("Sandbox $id does not have a CorDapp bundle.")
    } catch (e: ClassNotFoundException) {
        throw SandboxException("Class $className could not be loaded from sandbox $id.", e)
    } catch (e: IllegalStateException) {
        throw SandboxException("The bundle $cordappBundle in sandbox $id has been uninstalled.", e)
    }

    override fun containsBundle(bundle: Bundle) = bundle in bundles

    override fun containsClass(klass: Class<*>) = bundleUtils.getBundle(klass) in bundles

    override fun getBundle(klass: Class<*>) = bundleUtils.getBundle(klass)
        ?: throw SandboxException("Class $klass is not loaded from any bundle.")

    override fun isCordappBundle(bundle: Bundle) = bundle == cordappBundle

    override fun hasVisibility(otherSandbox: Sandbox) = (otherSandbox == this) || (otherSandbox in visibleSandboxes)

    override fun grantVisibility(otherSandbox: Sandbox) {
        if (otherSandbox !== this) {
            visibleSandboxes.add(otherSandbox)
        }
    }

    override fun grantVisibility(otherSandboxes: Collection<Sandbox>): Unit = otherSandboxes.forEach(this::grantVisibility)

    override fun revokeVisibility(otherSandbox: Sandbox): Boolean {
        return (otherSandbox !== this) && visibleSandboxes.remove(otherSandbox)
    }

    override fun uninstallBundles() = bundles.forEach { bundle ->
        try {
            bundle.uninstall()
        } catch (e: BundleException) {
            throw SandboxException("The following bundle in sandbox $id could not be uninstalled: ${bundle.location}.", e)
        }
    }
}
