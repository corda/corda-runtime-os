@file:JvmName("AMQPStreams")
package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.ByteBufferInputStream
import net.corda.internal.serialization.ByteBufferOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import net.corda.internal.serialization.byteArrayOutput

fun InputStream.asByteBuffer(): ByteBuffer {
    return if (this is ByteBufferInputStream) {
        byteBuffer // BBIS has no other state, so this is perfectly safe.
    } else {
        ByteBuffer.wrap(byteArrayOutput {
            copyTo(it)
        })
    }
}

fun <T> OutputStream.alsoAsByteBuffer(remaining: Int, task: (ByteBuffer) -> T): T {
    return if (this is ByteBufferOutputStream) {
        alsoAsByteBuffer(remaining, task)
    } else {
        ByteBufferOutputStream(64 * 1024).use {
            val result = it.alsoAsByteBuffer(remaining, task)
            it.copyTo(this)
            result
        }
    }
}
