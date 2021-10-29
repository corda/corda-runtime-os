package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic

// Make sure all serialization calls in this test don't get stomped on by anything else
val TESTING_CONTEXT = SerializationContextImpl(
    amqpMagic,
    SerializationContextImpl::class.java.classLoader,
    GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
    emptyMap(),
    true,
    SerializationContext.UseCase.Testing,
    null
)
