package net.corda.internal.serialization.amqp

import net.corda.sandbox.SandboxGroup

/**
 * This interface isolates the methods from SandboxGroup which are used in serialization, enabling non-OSGi
 * test contexts to use serialization libraries and mock this class without importing references to OSGi.
 *
 * @param sandboxGroup The sandbox group to which to delegate methods.
 */
class ClassloadingContextImpl(private val sandboxGroup: SandboxGroup) : ClassloadingContext {
    override fun getClass(className: String, serialisedClassTag: String): Class<*> {
        return sandboxGroup.getClass(className, serialisedClassTag)
    }

    override fun loadClassFromPublicBundles(className: String): Class<*>? {
        return sandboxGroup.loadClassFromPublicBundles(className)
    }

    override fun loadClassFromMainBundles(className: String): Class<*> {
        return sandboxGroup.loadClassFromMainBundles(className)
    }

    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> {
        return sandboxGroup.loadClassFromMainBundles(className, type)
    }

    override fun getEvolvableTag(klass: Class<*>): String {
        return sandboxGroup.getEvolvableTag(klass)
    }
}
