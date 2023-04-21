package net.corda.impl.serialization.encoding

import aQute.bnd.annotation.spi.ServiceProvider
import net.corda.internal.serialization.encoding.Encoder
import net.corda.internal.serialization.encoding.EncoderService
import net.corda.internal.serialization.encoding.EncoderType

@Suppress("unused")
@ServiceProvider(EncoderService::class)
class EncoderServiceFactory : EncoderService {
    override fun get(encoderType: EncoderType): Encoder {
        return when (encoderType) {
            EncoderType.SNAPPY -> SnappyEncoderImpl()
            EncoderType.DEFLATE -> DeflateEncoderImpl()
        }
    }
}
