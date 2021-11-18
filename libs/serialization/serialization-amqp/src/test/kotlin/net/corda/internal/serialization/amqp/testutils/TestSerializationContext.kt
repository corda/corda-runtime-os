@file:JvmName("TestSerializationContext")

package net.corda.internal.serialization.amqp.testutils

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.base.util.uncheckedCast

private class MockSandboxGroup : SandboxGroup {
        override val cpks: Collection<CPK> = emptyList()
        override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> =
                uncheckedCast(Class.forName(className))
        override fun getClass(className: String, serialisedClassTag: String) = Class.forName(className)
        override fun getStaticTag(klass: Class<*>): String = "S;bundle;sandbox"
        override fun getEvolvableTag(klass: Class<*>) = "E;bundle;sandbox"
}

val testSerializationContext: SerializationContext
        get() = SerializationContextImpl(
                preferredSerializationVersion = amqpMagic,
                whitelist = AllWhitelist,
                properties = mutableMapOf(),
                objectReferencesEnabled = false,
                useCase = SerializationContext.UseCase.Testing,
                encoding = null
        ).withSandboxGroup(MockSandboxGroup())