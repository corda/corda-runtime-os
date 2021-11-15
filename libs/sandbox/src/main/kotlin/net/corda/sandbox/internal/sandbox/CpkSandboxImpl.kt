package net.corda.sandbox.internal.sandbox

import net.corda.packaging.CPK
import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle
import java.util.UUID

/** Extends [SandboxImpl] to implement [CpkSandbox]. */
internal class CpkSandboxImpl(
    id: UUID,
    override val cpk: CPK,
    override val mainBundle: Bundle,
    privateBundles: Set<Bundle>
) : SandboxImpl(id, setOf(mainBundle), privateBundles), CpkSandbox {

    override fun loadClassFromMainBundle(className: String): Class<*> = try {
        mainBundle.loadClass(className)
    } catch (e: ClassNotFoundException) {
        throw SandboxException("The class $className cannot be found in bundle $mainBundle in sandbox $id.", e)
    } catch (e: IllegalStateException) {
        throw SandboxException("The bundle $mainBundle in sandbox $id has been uninstalled.", e)
    }
}
