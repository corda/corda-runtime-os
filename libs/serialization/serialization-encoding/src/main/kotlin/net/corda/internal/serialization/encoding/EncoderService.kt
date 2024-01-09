package net.corda.internal.serialization.encoding

/**
 * Interface for Encoder service providers
 */
interface EncoderService {
    fun get(encoderType: EncoderType) : Encoder
}
