package net.corda.sandbox.internal.sandbox

import net.corda.packaging.Cpk
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.osgi.framework.Bundle
import java.util.UUID

/** Extends [SandboxImpl] to implement [CpkSandboxInternal]. */
internal class CpkSandboxImpl(
    bundleUtils: BundleUtils,
    id: UUID,
    override val cpk: Cpk.Expanded,
    override val cordappBundle: Bundle,
    privateBundles: Set<Bundle>
) : SandboxImpl(bundleUtils, id, setOf(cordappBundle), privateBundles), CpkSandboxInternal {

    override fun loadClass(className: String): Class<*> = try {
        cordappBundle.loadClass(className)
    } catch (e: ClassNotFoundException) {
        throw SandboxException("Class $className could not be loaded from sandbox $id.", e)
    } catch (e: IllegalStateException) {
        throw SandboxException("The bundle $cordappBundle in sandbox $id has been uninstalled.", e)
    }

    override fun cordappBundleContainsClass(className: String) = try {
        loadClass(className)
        true
    } catch (ex: SandboxException) {
        false
    }
}
