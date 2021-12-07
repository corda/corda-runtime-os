@file:JvmName("TestSerializationContext")

package net.corda.internal.serialization.amqp.testutils

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext

private class MockSandboxGroup : SandboxGroup {
        override val cpks: Collection<CPK> = emptyList()

        override fun loadClassFromMainBundles(className: String): Class<*> = Class.forName(className)
        override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> =
                Class.forName(className).asSubclass(type)
        override fun getClass(className: String, serialisedClassTag: String) = Class.forName(className)
        override fun getStaticTag(klass: Class<*>): String = "S;bundle;sandbox"
        override fun getEvolvableTag(klass: Class<*>) = "E;bundle;sandbox"
}

@JvmField
val testSerializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        whitelist = AllWhitelist,
        properties = mutableMapOf(),
        objectReferencesEnabled = false,
        useCase = SerializationContext.UseCase.Testing,
        encoding = null,
        sandboxGroup = MockSandboxGroup()
)