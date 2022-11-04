package net.corda.sandboxgroupcontext

/**
 * Get an object from the sandbox group context object cache using an inline method instead, e.g.
 *
 *     val dog = ctx.getObjectByKey<Dog>("Fido")!!
 *
 * @return null if not found
 */
inline fun <reified T : Any> SandboxGroupContext.getObjectByKey(key: String) = this.get(key, T::class.java)

/**
 * Fetch the set of singleton services created for use by this sandbox.
 */
fun SandboxGroupContext.getSandboxSingletonServices(): Set<Any> = getObjectByKey(SANDBOX_SINGLETONS) ?: emptySet()
