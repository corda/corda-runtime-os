package net.corda.libs.statemanager.impl.compression.impl

import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.api.CompressionType.Companion.HEADER_SIZE
import net.corda.libs.statemanager.impl.compression.CompressionService
import net.corda.utilities.compressSnappy
import net.corda.utilities.debug
import net.corda.utilities.decompressSnappy
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [CompressionService::class])
class CompressionServiceImpl : CompressionService {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun writeBytes(bytes: ByteArray, compressionType: CompressionType): ByteArray {
        return when (compressionType) {
            CompressionType.NONE -> bytes
            CompressionType.SNAPPY -> compressionType.getHeader() + bytes.compressSnappy()
            else -> bytes
        }
    }

    override fun readBytes(bytes: ByteArray): ByteArray {
        if (bytes.size < HEADER_SIZE) {
            log.debug { "Read ByteArray from state manager whose size was less than 4" }
            return bytes
        }

        val headerBytes = bytes.copyOfRange(0, HEADER_SIZE)
        val compressionType = try {
            CompressionType.fromHeader(headerBytes)
        } catch (ex: Exception) {
            CompressionType.NONE
        }

        return when (compressionType) {
            CompressionType.NONE -> bytes
            CompressionType.SNAPPY -> bytes.copyOfRange(HEADER_SIZE, bytes.size).decompressSnappy()
            else -> bytes
        }
    }
}
