package net.corda.internal.serialization

import net.corda.base.internal.ByteSequence
import net.corda.base.internal.OpaqueBytes
import net.corda.internal.serialization.OrdinalBits.OrdinalWriter
import net.corda.internal.serialization.encoding.Encoder
import net.corda.internal.serialization.encoding.EncoderService
import net.corda.internal.serialization.encoding.EncoderServiceFactory
import net.corda.internal.serialization.encoding.EncoderType
import net.corda.serialization.SerializationEncoding
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    private val bufferView = slice()
    fun consume(data: ByteSequence): ByteBuffer? {
        return if (data.slice(0, size) == bufferView) data.slice(size) else null
    }
}

enum class SectionId : OrdinalWriter {
    /** Serialization data follows, and then discard the rest of the stream (if any) as legacy data may have trailing garbage. */
    DATA_AND_STOP,

    /** Identical behaviour to [DATA_AND_STOP], historically used for Kryo. Do not use in new code. */
    ALT_DATA_AND_STOP,

    /** The ordinal of a [CordaSerializationEncoding] follows, which should be used to decode the remainder of the stream. */
    ENCODING;

    companion object {
        val reader = OrdinalReader(values())
    }

    override val bits = OrdinalBits(ordinal)
}

enum class CordaSerializationEncoding(private val encoderType: EncoderType) : SerializationEncoding, OrdinalWriter {
    DEFLATE(EncoderType.DEFLATE),
    SNAPPY(EncoderType.SNAPPY);

    companion object {
        val reader = OrdinalReader(values())

        /**
         * Create singleton instance of [EncoderService].  Thread safe and stateless.
         */
        private val encoderService: EncoderService = EncoderServiceFactory()

        /**
         * Get an [Encoder] of the specified type
         */
        fun get(encoderType: EncoderType): Encoder = encoderService.get(encoderType)
    }

    override val bits = OrdinalBits(ordinal)
    fun compress(stream: OutputStream): OutputStream = get(encoderType).compress(stream)
    fun decompress(stream: InputStream): InputStream = get(encoderType).decompress(stream)
}

const val encodingNotPermittedFormat = "Encoding not permitted: %s"

