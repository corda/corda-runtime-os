@file:JvmName("TestSerializationContext")

package net.corda.internal.serialization.amqp.helper

import java.util.UUID
import java.util.function.Supplier
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_SERIALIZER_FACTORY
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.serialization.SerializationContext
import org.osgi.framework.Bundle

private class MockSandboxGroup(
    override val id: UUID,
    private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader()
) : SandboxGroup {
    override val metadata: Map<Bundle, CpkMetadata> = emptyMap()

    override fun loadClassFromMainBundles(className: String): Class<*> =
        Class.forName(className, false, classLoader)
    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> =
        Class.forName(className, false, classLoader).asSubclass(type)
    override fun getClass(className: String, serialisedClassTag: String): Class<*> = Class.forName(className)
    override fun loadClassFromPublicBundles(className: String): Class<*>? =
        Class.forName(className, false, classLoader)

    override fun getStaticTag(klass: Class<*>): String = "S;bundle;sandbox"
    override fun getEvolvableTag(klass: Class<*>) = "E;bundle;sandbox"
}

@JvmField
val testSerializationContext = SerializationContextImpl(
    preferredSerializationVersion = amqpMagic,
    properties = mutableMapOf(),
    objectReferencesEnabled = false,
    useCase = SerializationContext.UseCase.Testing,
    encoding = null,
    sandboxGroup = MockSandboxGroup(UUID.randomUUID())
)

/**
 * Create a fresh [SerializerFactory] for this [SandboxGroupContext].
 * This allows serialization / deserialization tests without sharing serializers.
 */
fun SandboxGroupContext.createSerializerFactory(): SerializerFactory {
    return getObjectByKey<Supplier<SerializerFactory>>(AMQP_SERIALIZER_FACTORY)?.get()
        ?: throw IllegalStateException("Could not create new AMQP SerializerFactory")
}
