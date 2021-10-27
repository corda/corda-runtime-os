@file:JvmName("SharedContexts")
package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.v5.serialization.EncodingWhitelist
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationEncoding

val AMQP_P2P_CONTEXT = SerializationContextImpl(
        amqpMagic,
        SerializationContextImpl::class.java.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.P2P,
        null
)

object AlwaysAcceptEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = true
}