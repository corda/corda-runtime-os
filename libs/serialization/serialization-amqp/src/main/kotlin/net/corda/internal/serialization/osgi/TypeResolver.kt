package net.corda.internal.serialization.osgi

import net.corda.sandbox.SandboxGroup

object TypeResolver {

    fun resolve(className: String, sandboxGroup: SandboxGroup): Class<*>
            = sandboxGroup.loadClassFromMainBundles(className, Any::class.java)
}
