package net.corda.blobinspector

import java.io.InputStream

interface SerializationFormatDecoder {
    fun decode(stream: InputStream, recurseDepth: Int, originalBytes: ByteArray, optionalAMQP: Boolean): DecodedBytes
    fun duplicate(): SerializationFormatDecoder
}
