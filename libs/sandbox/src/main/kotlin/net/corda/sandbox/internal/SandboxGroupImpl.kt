package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import java.util.NavigableMap

class SandboxGroupImpl(private val sandboxesById: NavigableMap<Cpk.Identifier, Sandbox>) : SandboxGroup {
    override val sandboxes = sandboxesById.values

    override fun getSandbox(cpkIdentifier: Cpk.Identifier) = sandboxesById[cpkIdentifier]
            ?: throw SandboxException("No sandbox was found in the group that had the CPK identifier $cpkIdentifier.")

    override fun loadClass(cpkIdentifier: Cpk.Identifier, className: String) = getSandbox(cpkIdentifier).loadClass(className)

    override fun <T : Any> loadClass(className: String, type: Class<T>): Class<out T> {
        var klass: Class<*>? = null
        var cause = SandboxException("Sandbox group does not contain any sandboxes")
        for (sandbox in sandboxes) {
            try {
                klass = sandbox.loadClass(className)
                break
            }
            catch (ex: SandboxException) {
                cause = ex
                continue
            }
        }
        if (klass == null) {
            throw SandboxException("Class $className could not be not found in sandbox group", cause)
        }
        return try {
            klass.asSubclass(type)
        } catch (e: ClassCastException) {
            throw SandboxException("Class $className was found in sandbox, but was not of the provided type " +
                    "$type.")
        }
    }


    override fun classCount(className: String): Int {
        var count = 0
        for (sandbox in sandboxes) {
            try {
                sandbox.loadClass(className)
                count++
            }
            catch (ex: SandboxException) {
                continue
            }
        }
        return count
    }
}