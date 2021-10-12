package net.corda.sandbox.internal.sandbox

import net.corda.packaging.CPK
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.osgi.framework.Bundle
import java.util.UUID

/** Extends [SandboxImpl] to implement [CpkSandboxInternal]. */
internal class CpkSandboxImpl(
    bundleUtils: BundleUtils,
    id: UUID,
    override val cpk: CPK,
    override val cordappBundle: Bundle,
    privateBundles: Set<Bundle>
) : SandboxImpl(bundleUtils, id, setOf(cordappBundle), privateBundles), CpkSandboxInternal {

    override fun loadClassFromCordappBundle(className: String): Class<*> = try {
        cordappBundle.loadClass(className)
    } catch (e: ClassNotFoundException) {
        throw SandboxException("The class $className cannot be found in bundle $cordappBundle in sandbox $id.", e)
    } catch (e: IllegalStateException) {
        throw SandboxException("The bundle $cordappBundle in sandbox $id has been uninstalled.", e)
    }

    override fun cordappBundleContainsClass(className: String) = try {
        loadClassFromCordappBundle(className)
        true
    } catch (e: SandboxException) {
        false
    }
}
