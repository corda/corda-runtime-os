package net.corda.blobinspector.metadata

import net.corda.blobinspector.ByteSequence
import net.corda.blobinspector.DecodedBytes
import net.corda.blobinspector.SerializationFormatDecoder
import net.corda.blobinspector.readFully
import net.corda.blobinspector.sequence
import java.io.InputStream

class MetadataSerializationFormatDecoder(private val recurse: (ByteSequence, Int, Boolean) -> Any?) : SerializationFormatDecoder {
    override fun duplicate(): SerializationFormatDecoder {
        return MetadataSerializationFormatDecoder(recurse)
    }

    override fun decode(
        stream: InputStream,
        recurseDepth: Int,
        originalBytes: ByteArray,
        includeOriginalBytes: Boolean,
        hasHeader: Boolean
    ): DecodedBytes {
        val bytes = stream.readFully()
        val version = if (hasHeader) bytes.sequence().subSequence(0, 1).toHexString() else "00"
        println("Metadata schema version = $version")
        return MetadataDecodedBytes(bytes.decodeToString())
    }

    private class MetadataDecodedBytes(override val result: Any?) : DecodedBytes
}
