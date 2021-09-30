package net.corda.impl.serialization.encoding

import net.corda.internal.serialization.encoding.Encoder
import net.corda.internal.serialization.encoding.EncoderService
import net.corda.internal.serialization.encoding.EncoderType
import org.osgi.service.component.annotations.Component

@Suppress("unused")
@Component(service = [EncoderService::class])
class EncoderServiceFactory : EncoderService {
    override fun get(encoderType: EncoderType): Encoder {
        return when (encoderType) {
            EncoderType.SNAPPY -> SnappyEncoderImpl()
            EncoderType.DEFLATE -> DeflateEncoderImpl()
        }
    }
}
