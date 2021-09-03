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
 * @param bundleUtils The [BundleUtils] that all OSGi activity are delegated to for testing purposes
 * @param bundles The set of [Bundle]s in this sandbox
 */
internal open class SandboxImpl(
    private val bundleUtils: BundleUtils,
    override val id: UUID,
    internal val bundles: Set<Bundle>
) : SandboxInternal {
    // The other sandboxes whose services, bundles and events this sandbox can receive.
    private val visibleSandboxes = ConcurrentHashMap.newKeySet<Sandbox>()

    override fun containsBundle(bundle: Bundle) = bundle in bundles

    override fun containsClass(klass: Class<*>) = bundleUtils.getBundle(klass) in bundles

    override fun getBundle(klass: Class<*>) = bundleUtils.getBundle(klass)
        ?: throw SandboxException("Class $klass is not loaded from any bundle.")

    override fun hasVisibility(otherSandbox: Sandbox) = (otherSandbox == this) || (otherSandbox in visibleSandboxes)

    override fun grantVisibility(otherSandbox: Sandbox) {
        if (otherSandbox !== this) {
            visibleSandboxes.add(otherSandbox)
        }
    }

    override fun grantVisibility(otherSandboxes: Collection<Sandbox>): Unit =
        otherSandboxes.forEach { otherSandbox ->
            if (otherSandbox !== this) {
                visibleSandboxes.add(otherSandbox)
            }
        }
}