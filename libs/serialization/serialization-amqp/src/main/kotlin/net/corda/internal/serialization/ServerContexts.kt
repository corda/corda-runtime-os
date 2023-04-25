@file:JvmName("ServerContexts")
package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.SerializationContext

/*
 * Serialisation contexts for the server.
 * These have been refactored into a separate file to prevent
 * clients from trying to instantiate any of them.
 *
 * NOTE: The [AMQP_STORAGE_CONTEXT]
 * CANNOT always be instantiated outside of the server and so
 * MUST be kept separate!
 */

val AMQP_STORAGE_CONTEXT = SerializationContextImpl(
    preferredSerializationVersion = amqpMagic,
    properties = emptyMap(),
    objectReferencesEnabled = false,
    useCase = SerializationContext.UseCase.Storage,
    encoding = null,
    encodingAllowList = AlwaysAcceptEncodingAllowList
)
