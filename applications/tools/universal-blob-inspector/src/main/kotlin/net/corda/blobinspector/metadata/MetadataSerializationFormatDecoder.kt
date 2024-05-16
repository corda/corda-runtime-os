package net.corda.blobinspector.metadata

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.blobinspector.ByteSequence
import net.corda.blobinspector.DecodedBytes
import net.corda.blobinspector.SerializationFormatDecoder
import net.corda.blobinspector.readFully
import net.corda.blobinspector.sequence
import java.io.InputStream

class MetadataSerializationFormatDecoder(
    private val recurse: (ByteSequence, Int, Boolean) -> Any?,
    private val hasHeader: Boolean
) : SerializationFormatDecoder {
    override fun duplicate(): SerializationFormatDecoder {
        return MetadataSerializationFormatDecoder(recurse, hasHeader)
    }

    override fun decode(stream: InputStream, recurseDepth: Int, originalBytes: ByteArray, includeOriginalBytes: Boolean): DecodedBytes {
        val bytes = stream.readFully()
        val (version, jsonBytes) = extractVersionAndBytes(bytes)
        println("Metadata schema version = $version")
        val objectMapper = ObjectMapper()
        val jsonMap = objectMapper.readValue(jsonBytes.decodeToString(), Map::class.java)

        val rendering: MutableMap<String, Any?> = mutableMapOf("_value" to jsonMap)
        if (includeOriginalBytes) {
            rendering["_bytes"] = originalBytes
        }
        return MetadataDecodedBytes(rendering)
    }

    private fun extractVersionAndBytes(bytes: ByteArray): Pair<String, ByteArray> {
        return if (hasHeader) {
            bytes.sequence().subSequence(0, 1).toHexString() to bytes.sliceArray(1..bytes.lastIndex)
        } else {
            "00" to bytes
        }
    }

    private class MetadataDecodedBytes(override val result: Any?) : DecodedBytes
}
