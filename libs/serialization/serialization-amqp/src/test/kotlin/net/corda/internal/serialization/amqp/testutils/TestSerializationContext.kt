@file:JvmName("TestSerializationContext")

package net.corda.internal.serialization.amqp.testutils

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.SerializationContext


val testSerializationContext: SerializationContext
        get() = SerializationContextImpl(
                preferredSerializationVersion = amqpMagic,
                whitelist = AllWhitelist,
                properties = mutableMapOf(),
                objectReferencesEnabled = false,
                useCase = SerializationContext.UseCase.Testing,
                encoding = null
        )