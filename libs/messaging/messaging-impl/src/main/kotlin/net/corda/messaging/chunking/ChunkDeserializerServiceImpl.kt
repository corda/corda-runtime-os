package net.corda.messaging.chunking

import java.io.ByteArrayOutputStream
import java.util.function.Consumer
import net.corda.chunking.Constants
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.messaging.api.chunking.ChunkDeserializerService
import net.corda.messaging.api.chunking.ConsumerChunkDeserializerService
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory

/**
 * Service to reassemble chunked messages into their original values.
 * Executes the [onError] handler if anything goes wrong with validation of checksums.
 * Deserialization errors are handled already within the [keyDeserializer] and [valueDeserializer]
 */
class ChunkDeserializerServiceImpl<K : Any, V : Any>(
    private val keyDeserializer: CordaAvroDeserializer<K>,
    private val valueDeserializer: CordaAvroDeserializer<V>,
    private val onError: Consumer<ByteArray>,
    private val platformDigestService: PlatformDigestService
) : ConsumerChunkDeserializerService<K, V>, ChunkDeserializerService<V> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun assembleChunks(chunks: Map<ChunkKey, Chunk>): Pair<K, V>? {
        val realKey = keyDeserializer.deserialize(chunks.keys.first().realKey.array()) ?: return null
        val value = assembleChunks(chunks.values.toList()) ?: return null
        return Pair(realKey, value)
    }

    override fun assembleChunks(chunks: List<Chunk>): V? {
        val dataList = chunks.sortedBy { it.partNumber }.map { it.data.array() }
        val dataSingleArray = concat(dataList)
        return try {
            val checksum = getCheckSumFromFinalChunk(chunks)
            logger.debug { "validating chunks for bytes size ${dataSingleArray.size}, chunk id ${chunks.first().requestId}" }
            validateBytes(dataSingleArray, checksum.array())
            valueDeserializer.deserialize(dataSingleArray)
        } catch (ex: IllegalArgumentException) {
            logger.warn("Failed to deserialize chunks due to: ${ex.message} ")
            onError.accept(dataSingleArray)
            null
        }
    }

    /**
     * Find and get the checksum as bytes from the chunks received
     * @throws IllegalArgumentException when no checksum is found
     */
    private fun getCheckSumFromFinalChunk(chunks: List<Chunk>) =
        (chunks.find { it.checksum != null }?.checksum?.bytes
            ?: throw IllegalArgumentException(Constants.SECURE_HASH_MISSING_ERROR))

    /**
     * Validate that a [SecureHash] is correct for the given [receivedBytes] and [messageDigestBytes]
     * @throws IllegalArgumentException if the given message digest does not match the recieved bytes
     */
    private fun validateBytes(receivedBytes: ByteArray, messageDigestBytes: ByteArray) {
        val receivedDigest = platformDigestService.hash(receivedBytes, DigestAlgorithmName.SHA2_256)
        val expectedDigest = SecureHash(DigestAlgorithmName.SHA2_256.name, messageDigestBytes)
        if (receivedDigest != expectedDigest) {
            throw IllegalArgumentException(Constants.SECURE_HASH_VALIDATION_ERROR)
        }
    }

    /**
     * Assemble a single ByteArray from multiple [byteArrays]
     */
    private fun concat(byteArrays: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        byteArrays.forEach { out.write(it) }
        return out.toByteArray()
    }
}
