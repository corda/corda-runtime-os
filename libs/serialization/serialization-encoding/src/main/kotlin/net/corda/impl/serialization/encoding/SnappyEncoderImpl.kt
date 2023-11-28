package net.corda.impl.serialization.encoding

import net.corda.internal.serialization.encoding.Encoder
import org.iq80.snappy.SnappyFramedInputStream
import org.iq80.snappy.SnappyFramedOutputStream
import org.osgi.service.component.annotations.Component
import java.io.InputStream
import java.io.OutputStream

@Suppress("unused")
@Component(name = "snappy")
class SnappyEncoderImpl : Encoder {
    override fun compress(output: OutputStream) = FlushAverseOutputStream(SnappyFramedOutputStream(output))
    override fun decompress(input: InputStream) = SnappyFramedInputStream(input, false)
}
