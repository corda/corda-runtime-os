package net.corda.sandboxgroupcontext

/**
 * Get an object from the sandbox group context object cache using an inline method instead, e.g.
 *
 *     val dog = ctx.getObjectByKey<Dog>("Fido")!!
 *
 * @return null if not found
 */
inline fun <reified T : Any> SandboxGroupContext.getObjectByKey(key: String) = get(key, T::class.java)

/**
 * Fetch service instances previously created using [SandboxGroupContextComponent.registerMetadataServices].
 */
@Suppress("KDocUnresolvedReference")
fun <T : Any> SandboxGroupContext.getMetadataServices(type: Class<T>): Set<T> = getObjectByKey(type.name) ?: emptySet()

inline fun <reified T : Any> SandboxGroupContext.getMetadataServices(): Set<T> = getMetadataServices(T::class.java)

/**
 * Fetch the set of singleton services created for use by this sandbox.
 */
fun SandboxGroupContext.getSandboxSingletonServices(): Set<Any> = getObjectByKey(SANDBOX_SINGLETONS) ?: emptySet()

/**
 * Fetch the single service of the given type from the sandbox's set of singletons.
 */
fun <T : Any> SandboxGroupContext.getSandboxSingletonService(type: Class<T>): T {
    return getSandboxSingletonServices().filterIsInstance(type).singleOrNull()
        ?: throw IllegalStateException("${type.name} service missing from sandbox")
}

inline fun <reified T : Any> SandboxGroupContext.getSandboxSingletonService(): T = getSandboxSingletonService(T::class.java)
