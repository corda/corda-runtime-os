package net.corda.internal.serialization.encoding

/**
 * The type of Encoder we support
 */
enum class EncoderType {
    DEFLATE,
    SNAPPY,
    NOOP
}
