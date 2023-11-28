package net.corda.internal.serialization.encoding

/**
 * Get an encoder using [java.util.ServiceLoader]
 *
 * Usage:
 *
 *    ServiceLoader<EncoderService> loader = ServiceLoader.load(EncoderService.class);
 */
interface EncoderService {
    fun get(encoderType: EncoderType): Encoder
}
