package net.corda.blobinspector

import java.io.InputStream

interface SerializationFormatDecoder {
    fun decode(stream: InputStream, recurseDepth: Int, originalBytes: ByteArray): DecodedBytes
    fun duplicate(): SerializationFormatDecoder
}
