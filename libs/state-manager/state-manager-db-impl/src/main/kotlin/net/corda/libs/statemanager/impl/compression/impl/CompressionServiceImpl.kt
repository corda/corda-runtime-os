package net.corda.libs.statemanager.impl.compression.impl

import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.impl.compression.CompressionService
import org.osgi.service.component.annotations.Component

@Component(service = [CompressionService::class])
class CompressionServiceImpl: CompressionService {

    override fun writeBytes(bytes: ByteArray, compressionType: CompressionType): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readBytes(bytes: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }


}