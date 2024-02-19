package net.corda.libs.statemanager.impl.compression.impl

import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.impl.compression.CompressionService
import net.corda.utilities.compressSnappy
import net.corda.utilities.debug
import net.corda.utilities.decompressSnappy
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

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
        if (bytes.size < 4) {
            log.debug { "Read ByteArray from state manager whose size was less than 4" }
            return bytes
        }

        val headerBytes = bytes.copyOfRange(0, 4)
        val header = headerBytes.toString(StandardCharsets.UTF_8)
        val compressionType = try {
            CompressionType.valueOf(header)
        } catch (ex: Exception) { CompressionType.NONE }

        return when (compressionType) {
            CompressionType.NONE -> bytes
            CompressionType.SNAPPY -> bytes.copyOfRange(4, bytes.size).decompressSnappy()
            else -> bytes
        }
    }
}
