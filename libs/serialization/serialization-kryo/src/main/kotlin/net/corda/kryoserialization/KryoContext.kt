package net.corda.kryoserialization

import net.corda.v5.serialization.ClassWhitelist
import net.corda.v5.serialization.EncodingWhitelist
import net.corda.v5.serialization.SerializationEncoding

val KRYO_CHECKPOINT_CONTEXT = CheckpointSerializationContextImpl(
        SerializationDefaults.javaClass.classLoader,
        QuasarWhitelist,
        emptyMap(),
        true,
        null,
        AlwaysAcceptEncodingWhitelist
)

object AlwaysAcceptEncodingWhitelist : EncodingWhitelist {
        override fun acceptEncoding(encoding: SerializationEncoding) = true
}

object QuasarWhitelist : ClassWhitelist {
        override fun hasListed(type: Class<*>): Boolean = true
}
