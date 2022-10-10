@file:JvmName("TestSerializationContext")

package net.corda.internal.serialization.amqp.helper

import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.ClassloadingContext
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.SerializationContext

private class MockSandboxGroup(
    private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader()
) : ClassloadingContext {

    override fun loadClassFromMainBundles(className: String): Class<*> =
        Class.forName(className, false, classLoader)
    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> =
        Class.forName(className, false, classLoader).asSubclass(type)
    override fun getClass(className: String, serialisedClassTag: String): Class<*> = Class.forName(className)
    override fun loadClassFromPublicBundles(className: String): Class<*>? =
        Class.forName(className, false, classLoader)

    override fun getEvolvableTag(klass: Class<*>) = "E;bundle;sandbox"
}

@JvmField
val testSerializationContext = SerializationContextImpl(
    preferredSerializationVersion = amqpMagic,
    properties = mutableMapOf(),
    objectReferencesEnabled = false,
    useCase = SerializationContext.UseCase.Testing,
    encoding = null,
    sandboxGroup = MockSandboxGroup()
)