package net.corda.internal.serialization.encoding

import java.io.InputStream
import java.io.OutputStream

/**
 * [EncoderService] which returns a given [EncoderType] can be provided via the OSGi service registry
 * or java.util.ServiceLoader (see [EncoderService] )
 */
interface Encoder {
    fun compress(output: OutputStream): OutputStream
    fun decompress(input: InputStream): InputStream
}
