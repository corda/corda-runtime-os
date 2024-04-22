package net.corda.blobinspector.kryo

import net.corda.blobinspector.DecodedBytes
import net.corda.blobinspector.SerializationFormatDecoder
import java.io.InputStream

class KryoSerializationFormatDecoder : SerializationFormatDecoder {
    override fun decode(stream: InputStream, recurseDepth: Int, originalBytes: ByteArray): DecodedBytes {
        TODO("Not yet implemented")
    }

    override fun duplicate(): SerializationFormatDecoder {
        return this
    }
}
