package net.corda.sandboxgroupcontext

/**
 * Get an object from the sandbox group context object cache using an inline method instead, e.g.
 *
 *     val dog = ctx.getObjectByKey<Dog>("Fido")!!
 *
 * @return null if not found
 */
inline fun <reified T : Any> SandboxGroupContext.getObjectByKey(key: String) = this.get(key, T::class)

/**
 * Get an object instance uniquely identified by its class name from the sandbox group context object cache.
 * The class name of this class is used to store it.
 *
 *      val dog = ctx.getUniqueObject<Dog>()!!
 *
 * @return null if not found
 */
inline fun <reified T : Any> SandboxGroupContext.getUniqueObject() =
    this.get(T::class.qualifiedName.toString(), T::class)
