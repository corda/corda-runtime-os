package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import kotlin.reflect.KClass

/**
 * Serialization utilities, using the Kryo framework with a custom serializer for immutable data classes and a dead
 * simple, totally non-extensible binary (sub)format. Used exclusively within Corda for checkpointing flows as
 * it will happily deserialise literally anything, including malicious streams that would reconstruct classes
 * in invalid states and thus violating system invariants. In the context of checkpointing a Java stack, this is
 * absolutely the functionality we desire, for a stable binary wire format and persistence technology, we have
 * the AMQP implementation.
 */

fun Output.writeBytesWithLength(byteArray: ByteArray) {
    this.writeInt(byteArray.size, true)
    this.writeBytes(byteArray)
}

fun Input.readBytesWithLength(): ByteArray {
    val size = this.readInt(true)
    return this.readBytes(size)
}

inline fun <T : Any> Kryo.register(
        type: KClass<T>,
        crossinline read: (Kryo, Input) -> T,
        crossinline write: (Kryo, Output, T) -> Unit): Registration {
    return register(
            type.java,
            object : Serializer<T>() {
                override fun read(kryo: Kryo, input: Input, clazz: Class<T>): T = read(kryo, input)
                override fun write(kryo: Kryo, output: Output, obj: T) = write(kryo, output, obj)
            }
    )
}

val kryoMagic = CordaSerializationMagic("corda".toByteArray() + byteArrayOf(0, 0))
