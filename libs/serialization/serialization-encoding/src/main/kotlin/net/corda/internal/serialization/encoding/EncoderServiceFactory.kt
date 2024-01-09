package net.corda.internal.serialization.encoding

import net.corda.impl.serialization.encoding.DeflateEncoderImpl
import net.corda.impl.serialization.encoding.NoopEncoderImpl
import net.corda.impl.serialization.encoding.SnappyEncoderImpl

@Suppress("unused")
/**
 * Our (only) implementation of [EncoderService].  Thread safe and stateless.
 */
class EncoderServiceFactory : EncoderService {
    override fun get(encoderType: EncoderType): Encoder {
        return when (encoderType) {
            EncoderType.SNAPPY -> SnappyEncoderImpl()
            EncoderType.DEFLATE -> DeflateEncoderImpl()
            EncoderType.NOOP -> NoopEncoderImpl()
        }
    }
}
