@file:JvmName("SharedContexts")
package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.EncodingWhitelist
import net.corda.serialization.SerializationContext
import net.corda.serialization.SerializationEncoding

val AMQP_P2P_CONTEXT = SerializationContextImpl(
        amqpMagic,
        emptyMap(),
        true,
        SerializationContext.UseCase.P2P,
        null
)

object AlwaysAcceptEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = true
}
