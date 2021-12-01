package net.corda.sandbox.internal.sandbox

import aQute.bnd.header.OSGiHeader.parseHeader
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.EXPORT_PACKAGE
import java.util.Collections.unmodifiableSet
import java.util.UUID

/** Extends [SandboxImpl] to implement [CpkSandbox]. */
internal class CpkSandboxImpl(
    id: UUID,
    override val cpk: CPK,
    override val mainBundle: Bundle,
    privateBundles: Set<Bundle>
) : SandboxImpl(id, setOf(mainBundle), privateBundles), CpkSandbox {

    private val exportedPackages = mainBundle.headers[EXPORT_PACKAGE]?.let { exports ->
        unmodifiableSet(parseHeader(exports).keys)
    } ?: emptySet()

    override fun loadClassFromMainBundle(className: String): Class<*> = try {
        mainBundle.loadClass(className).also { clazz ->
            if (clazz.packageName !in exportedPackages) {
                throw SandboxException("The class $className cannot be found in bundle $mainBundle in sandbox $id.")
            }
        }
    } catch (e: ClassNotFoundException) {
        throw SandboxException("The class $className cannot be found in bundle $mainBundle in sandbox $id.", e)
    } catch (e: IllegalStateException) {
        throw SandboxException("The bundle $mainBundle in sandbox $id has been uninstalled.", e)
    }
}
