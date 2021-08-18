@file:JvmName("ClientContexts")

package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.v5.serialization.SerializationContext

/*
 * Serialisation contexts for the client.
 * These have been refactored into a separate file to prevent
 * servers from trying to instantiate any of them.
 */


val AMQP_RPC_CLIENT_CONTEXT = SerializationContextImpl(
        amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCClient,
        null
)
