package net.corda.impl.serialization.encoding

import java.io.IOException
import java.io.OutputStream

/**
 * Has an empty flush implementation.  This is because Kryo keeps calling flush all the time, which stops the Snappy
 * stream from building up big chunks to compress and instead keeps compressing small chunks giving terrible compression ratio.
 */
class FlushAverseOutputStream(private val delegate: OutputStream) : OutputStream() {
    @Throws(IOException::class)
    override fun write(b: Int) = delegate.write(b)

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)

    @Throws(IOException::class)
    override fun close() = delegate.use(OutputStream::flush)
}
