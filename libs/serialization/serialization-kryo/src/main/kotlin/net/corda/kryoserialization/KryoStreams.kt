@file:JvmName("KryoStreams")

package net.corda.kryoserialization

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.internal.serialization.encoding.Encoder
import net.corda.internal.serialization.encoding.EncoderServiceFactory
import net.corda.internal.serialization.encoding.EncoderType
import net.corda.utilities.LazyPool
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream

private val serializationBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) })

internal fun <T> kryoInput(underlying: InputStream, task: Input.() -> T): T {
    return serializationBufferPool.run {
        Input(it).use { input ->
            input.inputStream = underlying
            input.task()
        }
    }
}

internal fun <T> kryoOutput(
    encoder: Encoder = EncoderServiceFactory().get(EncoderType.NOOP),
    task: Output.() -> T
): ByteArray {
    return byteArrayOutput(encoder) { underlying ->
        serializationBufferPool.run {
            Output(it).use { output ->
                output.outputStream = underlying
                output.task()
            }
        }
    }
}

internal fun Output.substitute(transform: (OutputStream) -> OutputStream) {
    flush()
    outputStream = transform(outputStream)
}

internal fun Input.substitute(transform: (InputStream) -> InputStream) {
    inputStream = transform(SequenceInputStream(buffer.copyOfRange(position(), limit()).inputStream(), inputStream))
}

fun Output.writeBytesWithLength(byteArray: ByteArray) {
    this.writeInt(byteArray.size, true)
    this.writeBytes(byteArray)
}

fun Input.readBytesWithLength(): ByteArray {
    val size = this.readInt(true)
    return this.readBytes(size)
}
