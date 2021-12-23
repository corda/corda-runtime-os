package net.corda.sandboxgroupcontext

/**
 * Put an object into the sandbox group context object cache with an explicit key.
 *
 * @throws IllegalArgumentException if you call this again for the same type AND key
 */
inline fun <reified T : Any> MutableSandboxGroupContext.putObjectByKey(key: String, value: T) =
    this.put(key, value::class.java, value)

/**
 * Put an object into the sandbox group context object cache with an implicit key that is the class name.
 *
 * @throws IllegalArgumentException if you call this again for the same type
 */
inline fun <reified T : Any> MutableSandboxGroupContext.putUniqueObject(value: T) =
    this.put(value::class.java.name, value::class.java, value)
