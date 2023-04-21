package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

internal object AutoCloseableSerializer : Serializer<AutoCloseable>() {
    override fun write(kryo: Kryo, output: Output, closeable: AutoCloseable) {
        val message = "${closeable.javaClass.name}, which is a closeable resource, has been detected during flow " +
                "checkpointing. Restoring such resources across node restarts is not supported. Make sure code " +
                "accessing it is confined to a private method or the reference is null."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out AutoCloseable>) =
        throw IllegalStateException("Should not reach here!")
}

