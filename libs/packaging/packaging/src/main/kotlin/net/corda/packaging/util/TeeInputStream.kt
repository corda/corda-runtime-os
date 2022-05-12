package net.corda.packaging.util

import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * [InputStream] that also writes its content to the provided [OutputStream] while reading
 */
class TeeInputStream(inputStream : InputStream, private val destination : OutputStream): FilterInputStream(inputStream) {
    var written = 0
    override fun read(): Int {
        return super.read().also {
            if(it >= 0)  {
                destination.write(it)
                written++
            }
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return super.read(b, off, len).also {
            if(it > 0) {
                destination.write(b, off, it)
                written += it
            }
        }
    }

    override fun close() {
        destination.close()
        super.close()
    }
}