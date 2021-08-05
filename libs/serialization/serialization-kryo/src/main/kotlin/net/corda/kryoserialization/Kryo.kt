package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.v5.serialization.SerializedBytes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import kotlin.reflect.KClass

/**
 * Serialization utilities, using the Kryo framework with a custom serializer for immutable data classes and a dead
 * simple, totally non-extensible binary (sub)format. Used exclusively within Corda for checkpointing flows as
 * it will happily deserialise literally anything, including malicious streams that would reconstruct classes
 * in invalid states and thus violating system invariants. In the context of checkpointing a Java stack, this is
 * absolutely the functionality we desire, for a stable binary wire format and persistence technology, we have
 * the AMQP implementation.
 */

/**
 * A serializer that avoids writing the wrapper class to the byte stream, thus ensuring [SerializedBytes] is a pure
 * type safety hack.
 */
object SerializedBytesSerializer : Serializer<SerializedBytes<Any>>() {
    override fun write(kryo: Kryo, output: Output, obj: SerializedBytes<Any>) {
        output.writeVarInt(obj.size, true)
        obj.writeTo(output)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<SerializedBytes<Any>>): SerializedBytes<Any> {
        return SerializedBytes(input.readBytes(input.readVarInt(true)))
    }
}

// This is a temporary inefficient serializer for sending InputStreams through RPC. This may be done much more
// efficiently using Artemis's large message feature.
object InputStreamSerializer : Serializer<InputStream>() {
    override fun write(kryo: Kryo, output: Output, stream: InputStream) {
        val buffer = ByteArray(4096)
        while (true) {
            val numberOfBytesRead = stream.read(buffer)
            if (numberOfBytesRead != -1) {
                output.writeInt(numberOfBytesRead, true)
                output.writeBytes(buffer, 0, numberOfBytesRead)
            } else {
                output.writeInt(0, true)
                break
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<InputStream>): InputStream {
        val chunks = ArrayList<ByteArray>()
        while (true) {
            val chunk = input.readBytesWithLength()
            if (chunk.isEmpty()) {
                break
            } else {
                chunks.add(chunk)
            }
        }
        val flattened = ByteArray(chunks.sumBy { it.size })
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, flattened, offset, chunk.size)
            offset += chunk.size
        }
        return flattened.inputStream()
    }

}

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

/**
 * Use this method to mark any types which can have the same instance within it more than once. This will make sure
 * the serialised form is stable across multiple serialise-deserialise cycles. Using this on a type with internal cyclic
 * references will throw a stack overflow exception during serialisation.
 */
inline fun <reified T : Any> Kryo.noReferencesWithin() {
    register(T::class.java, NoReferencesSerializer(getSerializer(T::class.java)))
}

class NoReferencesSerializer<T>(private val baseSerializer: Serializer<T>) : Serializer<T>() {

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        return kryo.withoutReferences { baseSerializer.read(kryo, input, type) }
    }

    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.withoutReferences { baseSerializer.write(kryo, output, obj) }
    }
}

fun <T> Kryo.withoutReferences(block: () -> T): T {
    val previousValue = setReferences(false)
    try {
        return block()
    } finally {
        references = previousValue
    }
}

/** For serialising a Logger. */
object LoggerSerializer : Serializer<Logger>() {
    override fun write(kryo: Kryo, output: Output, obj: Logger) {
        output.writeString(obj.name)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Logger>): Logger {
        return LoggerFactory.getLogger(input.readString())
    }
}

fun Kryo.serializationContext(): SerializeAsTokenContextInternal? = context.get(serializationContextKey) as? SerializeAsTokenContextInternal
