package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.CpkSandbox
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.osgi.framework.Bundle
import java.util.UUID

/**
 * A [Sandbox] created from a CPK.
 *
 * @param cordappBundle The CPK's CorDapp bundle.
 */
internal class CpkSandboxImpl(
    bundleUtils: BundleUtils,
    id: UUID,
    override val cpk: Cpk.Expanded,
    private val cordappBundle: Bundle,
    otherBundles: Set<Bundle>
) : SandboxInternalAbstractImpl(bundleUtils, id, otherBundles + cordappBundle), CpkSandbox {

    override fun loadClass(className: String): Class<*> = try {
        cordappBundle.loadClass(className)
    } catch (e: ClassNotFoundException) {
        throw SandboxException("Class $className could not be loaded from sandbox $id.", e)
    } catch (e: IllegalStateException) {
        throw SandboxException("The bundle $cordappBundle in sandbox $id has been uninstalled.", e)
    }

    override fun isCordappBundle(bundle: Bundle) = bundle == cordappBundle
}
