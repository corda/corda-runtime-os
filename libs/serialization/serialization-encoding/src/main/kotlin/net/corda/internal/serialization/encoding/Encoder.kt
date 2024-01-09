package net.corda.internal.serialization.encoding

import java.io.InputStream
import java.io.OutputStream

/**
 * Interface for Encoder implementations of compress/decompress
 */
interface Encoder {
    fun compress(output: OutputStream): OutputStream
    fun decompress(input: InputStream): InputStream
}
