package net.corda.internal.serialization.amqp.testutils

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.SerializationContext

val serializationProperties: MutableMap<Any, Any> = mutableMapOf()

val testSerializationContext: SerializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        whitelist = AllWhitelist,
        properties = serializationProperties,
        objectReferencesEnabled = false,
        useCase = SerializationContext.UseCase.Testing,
        encoding = null)