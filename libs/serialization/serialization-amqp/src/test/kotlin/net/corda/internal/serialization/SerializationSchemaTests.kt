package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.v5.serialization.SerializationContext

// Make sure all serialization calls in this test don't get stomped on by anything else
val TESTING_CONTEXT = SerializationContextImpl(
    amqpMagic,
    SerializationDefaults.javaClass.classLoader,
    GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
    emptyMap(),
    true,
    SerializationContext.UseCase.Testing,
    null
)
