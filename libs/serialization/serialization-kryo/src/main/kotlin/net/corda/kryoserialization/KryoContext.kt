package net.corda.kryoserialization

import net.corda.kryoserialization.impl.CheckpointSerializationContextImpl
import net.corda.v5.serialization.ClassWhitelist

val KRYO_CHECKPOINT_CONTEXT = CheckpointSerializationContextImpl(
        KryoCheckpointSerializer::class.java.classLoader,
        QuasarWhitelist,
        emptyMap(),
        true,
        null
)

object QuasarWhitelist : ClassWhitelist {
        override fun hasListed(type: Class<*>): Boolean = true
}
