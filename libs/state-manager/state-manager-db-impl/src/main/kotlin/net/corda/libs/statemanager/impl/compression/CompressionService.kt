package net.corda.libs.statemanager.impl.compression

import net.corda.libs.statemanager.api.CompressionType

interface CompressionService {

    /**
     * Prepare [bytes] to be written to the underlying storage. If [compressionType] is set to something other than [CompressionType.NONE]
     * then the compression alogrithm chosen is applied to [bytes].
     * The [compressionType] used is prefixed to the bytes returned.
     * This allows the service to read the bytes from the storage and decompress using the appropriate algorithm.
     * @param bytes to be prepared to be written to the storage
     * @param compressionType The type of compression, if any, to be applied to [bytes]
     * @return The bytes to be written to the underlying storage
     */
    fun writeBytes(bytes: ByteArray, compressionType: CompressionType = CompressionType.NONE): ByteArray

    /**
     * Take [bytes] from the underlying storage and decompress them if needed using the compression algorithm applied when writing.
     * @param bytes read from the storage
     * @return Bytes read from the storage, decompressed if needed.
     */
    fun readBytes(bytes: ByteArray): ByteArray
}
