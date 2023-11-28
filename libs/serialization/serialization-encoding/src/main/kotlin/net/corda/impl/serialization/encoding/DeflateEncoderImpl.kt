package net.corda.impl.serialization.encoding

import net.corda.internal.serialization.encoding.Encoder
import org.osgi.service.component.annotations.Component
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

@Suppress("unused")
@Component(name = "deflate")
class DeflateEncoderImpl : Encoder {
    override fun compress(output: OutputStream) = DeflaterOutputStream(output)
    override fun decompress(input: InputStream) = InflaterInputStream(input)
}
