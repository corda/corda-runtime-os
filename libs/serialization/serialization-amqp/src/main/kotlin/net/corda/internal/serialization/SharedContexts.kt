@file:JvmName("SharedContexts")
package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.EncodingAllowList
import net.corda.serialization.SerializationContext
import net.corda.serialization.SerializationEncoding

val AMQP_P2P_CONTEXT = SerializationContextImpl(
        amqpMagic,
        emptyMap(),
        false, //todo revert this to true in CORE-12472
        SerializationContext.UseCase.P2P,
        null
)

object AlwaysAcceptEncodingAllowList : EncodingAllowList {
    override fun acceptEncoding(encoding: SerializationEncoding) = true
}
