package net.corda.impl.serialization.encoding

import net.corda.internal.serialization.encoding.Encoder
import java.io.InputStream
import java.io.OutputStream

@Suppress("unused")
class NoopEncoderImpl : Encoder {
    override fun compress(output: OutputStream) = output
    override fun decompress(input: InputStream) = input
}