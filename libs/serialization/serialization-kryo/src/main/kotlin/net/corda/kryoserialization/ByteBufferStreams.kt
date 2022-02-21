@file:JvmName("ByteBufferStreams")
package net.corda.kryoserialization

import net.corda.utilities.LazyPool
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Arrays

internal val serializeOutputStreamPool = LazyPool(
        clear = ByteBufferOutputStream::reset,
        shouldReturnToPool = { it.size() < 256 * 1024 }, // Discard if it grew too large
        newInstance = { ByteBufferOutputStream(64 * 1024) })

fun <T> byteArrayOutput(task: (ByteBufferOutputStream) -> T): ByteArray {
    return serializeOutputStreamPool.run { underlying ->
        task(underlying)
        underlying.toByteArray() // Must happen after close, to allow ZIP footer to be written for example.
    }
}

class ByteBufferOutputStream(size: Int) : ByteArrayOutputStream(size) {

    fun <T> alsoAsByteBuffer(remaining: Int, task: (ByteBuffer) -> T): T {
        val requiredCapacity = count + remaining
        if (requiredCapacity > buf.size) {
            buf = Arrays.copyOf(buf, requiredCapacity)
        }
        val buffer = ByteBuffer.wrap(buf, count, remaining)
        val result = task(buffer)
        count = buffer.position()
        return result
    }

    fun copyTo(stream: OutputStream) {
        stream.write(buf, 0, count)
    }
}
