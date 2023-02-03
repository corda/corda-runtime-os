package net.corda.sandbox.internal.sandbox

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleRevision.PACKAGE_NAMESPACE
import org.osgi.framework.wiring.BundleWiring
import java.util.UUID

/** Extends [SandboxImpl] to implement [CpkSandbox]. */
internal class CpkSandboxImpl(
    id: UUID,
    override val cpkMetadata: CpkMetadata,
    override val mainBundle: Bundle,
    privateBundles: Set<Bundle>
) : SandboxImpl(id, setOf(mainBundle), privateBundles), CpkSandbox {
    override fun loadClassFromMainBundle(className: String): Class<*> = try {
        mainBundle.loadClass(className).also { clazz ->
            if (!accept(clazz)) {
                throw SandboxException(
                    "The class $className cannot be loaded from bundle $mainBundle in sandbox $id, as " +
                            "the class was not found in the correct bundle."
                )
            }
        }
    } catch (e: ClassNotFoundException) {
        throw SandboxException("The class $className cannot be found in bundle $mainBundle in sandbox $id.", e)
    } catch (e: IllegalStateException) {
        throw SandboxException("The bundle $mainBundle in sandbox $id has been uninstalled.", e)
    }

    private fun accept(clazz: Class<*>): Boolean {
        return FrameworkUtil.getBundle(clazz).let { bundle ->
            // Accept Java platform classes, or classes from either our "main" or the system bundle.
            bundle == null || (bundle === mainBundle && bundle.exports(clazz)) || bundle.bundleId == SYSTEM_BUNDLE_ID
        }
    }

    private fun Bundle.exports(clazz: Class<*>): Boolean {
        return adapt(BundleWiring::class.java).getCapabilities(PACKAGE_NAMESPACE).any { capability ->
            capability.attributes[PACKAGE_NAMESPACE] == clazz.packageName
        }
    }
}
