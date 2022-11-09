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
 * Fetch the set of all singleton services created for use by this sandbox.
 */
fun SandboxGroupContext.getAllSandboxSingletonServices(): Set<Any> = getObjectByKey(SANDBOX_SINGLETONS) ?: emptySet()

/**
 * Fetch the set of this sandbox's singleton services that are of type [T].
 */
fun <T : Any> SandboxGroupContext.getSandboxSingletonServices(type: Class<T>): Set<T> {
    return getAllSandboxSingletonServices().filterIsInstanceTo(linkedSetOf(), type)
}

inline fun <reified T : Any> SandboxGroupContext.getSandboxSingletonServices(): Set<T> {
    return getSandboxSingletonServices(T::class.java)
}

/**
 * Fetch the single service of type [T] from the sandbox's set of singletons.
 */
fun <T : Any> SandboxGroupContext.getSandboxSingletonService(type: Class<T>): T {
    return getSandboxSingletonServices(type).singleOrNull()
        ?: throw IllegalStateException("${type.name} service missing from sandbox")
}

inline fun <reified T : Any> SandboxGroupContext.getSandboxSingletonService(): T = getSandboxSingletonService(T::class.java)
