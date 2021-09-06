package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import java.util.NavigableMap

internal class SandboxGroupImpl(private val sandboxesById: NavigableMap<Cpk.Identifier, CpkSandboxImpl>) : SandboxGroup {
    override val sandboxes = sandboxesById.values

    override fun getSandbox(cpkIdentifier: Cpk.Identifier) = sandboxesById[cpkIdentifier]
        ?: throw SandboxException("No sandbox was found in the group that had the CPK identifier $cpkIdentifier.")

    override fun loadClass(cpkIdentifier: Cpk.Identifier, className: String) =
        getSandbox(cpkIdentifier).loadClass(className)

    override fun <T : Any> loadClass(className: String, type: Class<T>): Class<out T> {
        val containingSandbox = sandboxes.find { sandbox -> sandbox.cordappBundleContainsClass(className) }
            ?: throw SandboxException("Class $className could not be not found in sandbox group.")

        return try {
            containingSandbox.loadClass(className).asSubclass(type)
        } catch (e: ClassCastException) {
            throw SandboxException(
                "Class $className was found in sandbox, but was not of the provided type $type."
            )
        }
    }

    override fun cordappClassCount(className: String) = sandboxes.count { sandbox ->
        sandbox.cordappBundleContainsClass(className)
    }
}