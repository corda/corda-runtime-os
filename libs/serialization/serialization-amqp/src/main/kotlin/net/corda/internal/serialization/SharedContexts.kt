@file:JvmName("SharedContexts")
package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.EncodingAllowList
import net.corda.serialization.SerializationContext
import net.corda.serialization.SerializationEncoding

val AMQP_P2P_CONTEXT = SerializationContextImpl(
    preferredSerializationVersion = amqpMagic,
    properties = emptyMap(),
    objectReferencesEnabled = true,
    useCase = SerializationContext.UseCase.P2P,
    encoding = CordaSerializationEncoding.SNAPPY
)

object AlwaysAcceptEncodingAllowList : EncodingAllowList {
    override fun acceptEncoding(encoding: SerializationEncoding) = true
}
