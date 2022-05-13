package net.corda.libs.packaging.internal

import java.io.FilterInputStream
import java.io.InputStream

/**
 * [InputStream] wrapper that prevents it from being closed, useful to pass an [InputStream] instance
 * to a method that closes the stream before it has been fully consumed
 * (and whose remaining content is still needed by the caller)
 */
internal class UncloseableInputStream(source : InputStream) : FilterInputStream(source) {
    override fun close() {}
}

