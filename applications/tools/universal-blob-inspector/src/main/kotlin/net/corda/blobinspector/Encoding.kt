package net.corda.blobinspector

import net.corda.blobinspector.amqp.AMQPSerializationFormatDecoder
import net.corda.blobinspector.amqp.DynamicDescriptorRegistry
import net.corda.blobinspector.amqp.Envelope
import net.corda.blobinspector.kryo.KryoSerializationFormatDecoder
import net.corda.blobinspector.metadata.MetadataSerializationFormatDecoder
import org.xerial.snappy.SnappyFramedInputStream
import java.io.InputStream
import java.util.zip.DeflaterInputStream

object Encoding {
    val cordaMagic = "corda".toByteArray().sequence()

    @Suppress("LongParameterList")
    private enum class Encodings(
        val magic: String,
        val primaryEncoding: String,
        @Suppress("Unused")
        val description: String,
        val compressionEncodingStart: Int,
        val compressionEncoding: CompressionEncoding,
        val serializationFormat: SerializationFormat
    ) {
        KRYO1("corda", "0001", "Corda 1+ Kryo", 7, CompressionEncoding.CORDA1, SerializationFormat.KRYO1),
        AMQP_ENT3("corda", "0100", "Corda OS 4+ / ENT 3+ AMQP", 7, CompressionEncoding.CORDA1, SerializationFormat.AMQP1),
        AMQP5GA("corda", "0400", "Corda 5 GA AMQP", 7, CompressionEncoding.CORDA1, SerializationFormat.AMQP5),

        // there are couple more trailing bytes "01" after primary encoding, which represents version of metadata schema.
        METADATA00("{\"com", "706F", "Corda 5.0+ Metadata", 0, CompressionEncoding.NONE, SerializationFormat.METADATA_NO_HEADER),
        METADATA01("corda", "0800", "Corda 5.2.1+ Metadata", 7, CompressionEncoding.NONE, SerializationFormat.METADATA)
    }

    private enum class CompressionEncoding {
        CORDA1 {
            override fun createStream(byteSequence: ByteSequence): InputStream {
                return when (SecondaryEncoding.values()[byteSequence[0].toInt()]) {
                    SecondaryEncoding.RAW_DATA_PLUS_PADDING -> NONE.createStream(byteSequence.subSequence(1))
                    SecondaryEncoding.RAW_DATA -> NONE.createStream(byteSequence.subSequence(1))
                    SecondaryEncoding.COMPRESSED -> {
                        when (Compression.values()[byteSequence[1].toInt()]) {
                            Compression.DEFLATE -> DEFLATE.createStream(byteSequence.subSequence(2))
                            Compression.SNAPPY -> SNAPPY.createStream(byteSequence.subSequence(2))
                        }
                    }
                }
            }
        },
        NONE {
            override fun createStream(byteSequence: ByteSequence): InputStream {
                return byteSequence.open()
            }
        },
        DEFLATE {
            override fun createStream(byteSequence: ByteSequence): InputStream {
                return verifyStream(DeflaterInputStream(byteSequence.open()))
            }
        },
        SNAPPY {
            override fun createStream(byteSequence: ByteSequence): InputStream {
                return verifyStream(SnappyFramedInputStream(byteSequence.open(), true))
            }
        };

        fun verifyStream(stream: InputStream): InputStream {
            val secondaryEncoding = SecondaryEncoding.values()[stream.read()]
            if (secondaryEncoding != SecondaryEncoding.RAW_DATA_PLUS_PADDING) {
                throw IllegalStateException(
                    "Was expecting secondary encoding inside compression to be ${SecondaryEncoding.RAW_DATA_PLUS_PADDING} " +
                        "but was $secondaryEncoding"
                )
            }
            return stream
        }

        abstract fun createStream(byteSequence: ByteSequence): InputStream
    }

    private enum class SerializationFormat(val decoder: SerializationFormatDecoder) {
        KRYO1(KryoSerializationFormatDecoder()),
        AMQP1(
            AMQPSerializationFormatDecoder(
                { bytes, depth, includeOriginalBytes, beVerbose ->
                    decodedBytes(
                        bytes,
                        recurseDepth = depth,
                        includeOriginalBytes = includeOriginalBytes,
                        beVerbose = beVerbose
                    ).result
                },
                { data -> Envelope.get(data) },
                { DynamicDescriptorRegistry() }
            )
        ),
        AMQP5(
            AMQPSerializationFormatDecoder(
                { bytes, depth, includeOriginalBytes, beVerbose ->
                    decodedBytes(
                        bytes,
                        recurseDepth = depth,
                        includeOriginalBytes = includeOriginalBytes,
                        beVerbose = beVerbose
                    ).result
                },
                { data -> Envelope.get(data, osgiBundles = true) },
                { DynamicDescriptorRegistry(lenientBuiltIns = true) }
            )
        ),
        METADATA(
            MetadataSerializationFormatDecoder(
                { bytes, depth, includeOriginalBytes, beVerbose ->
                    decodedBytes(
                        bytes,
                        recurseDepth = depth,
                        includeOriginalBytes = includeOriginalBytes,
                        beVerbose = beVerbose
                    ).result
                },
                true
            )
        ),
        METADATA_NO_HEADER(
            MetadataSerializationFormatDecoder(
                { bytes, depth, includeOriginalBytes, beVerbose ->
                    decodedBytes(
                        bytes,
                        recurseDepth = depth,
                        includeOriginalBytes = includeOriginalBytes,
                        beVerbose = beVerbose
                    ).result
                },
                false
            )
        )
    }

    private enum class SecondaryEncoding {
        /** Serialization data follows, and then discard the rest of the stream (if any) as legacy data may have trailing garbage. */
        RAW_DATA_PLUS_PADDING,

        /** Identical behaviour to [RAW_DATA_PLUS_PADDING], but without padding allowed and historically used for Kryo.
         * Do not use in new code.
         */
        RAW_DATA,

        /** The ordinal of a [Compression] follows, which should be used to decode the remainder of the stream. */
        COMPRESSED
    }

    private enum class Compression {
        DEFLATE,
        SNAPPY
    }

    private val cordaMagicHeaders = Encodings.values()
        .groupBy { it.magic.toByteArray().sequence() }
        .mapValues { (_, encodings: List<Encodings>) ->
            encodings.associateBy { encoding: Encodings ->
                encoding.primaryEncoding
            }
        }

    @Suppress("LongParameterList")
    fun decodedBytes(
        byteSequence: ByteSequence,
        includeOriginalBytes: Boolean,
        beVerbose: Boolean,
        compressionEncodingOverride: String? = null,
        compressionEncodingStartOverride: Int? = null,
        formatOverride: String? = null,
        recurseDepth: Int = 0
    ): DecodedBytes {
        fun allOverridesPresent() =
            compressionEncodingOverride != null && compressionEncodingStartOverride != null && formatOverride != null

        fun overriddenEncoding(): Triple<CompressionEncoding, Int, SerializationFormatDecoder> {
            if (beVerbose) println("Overridden encoding")
            val triple = Triple(
                CompressionEncoding.valueOf(compressionEncodingOverride!!),
                compressionEncodingStartOverride!!,
                SerializationFormat.valueOf(formatOverride!!).decoder.duplicate()
            )
            return triple
        }

        val originalBytes = byteSequence.copyBytes()
        val (compressionEncoding, compressionEncodingStart, decoder) = if (allOverridesPresent()) {
            overriddenEncoding()
        } else {
            extractEncoding(byteSequence, beVerbose)
        }
        compressionEncoding.createStream(byteSequence.subSequence(compressionEncodingStart)).use { stream ->
            return decoder.decode(stream, recurseDepth, originalBytes, includeOriginalBytes, beVerbose)
        }
    }

    private fun extractEncoding(
        byteSequence: ByteSequence,
        beVerbose: Boolean
    ): Triple<CompressionEncoding, Int, SerializationFormatDecoder> {
        val magicByteSequence = byteSequence.take(5)
        val magicType = cordaMagicHeaders[magicByteSequence]
        requireNotNull(magicType) { "Unknown magic header $magicByteSequence." }

        val primaryEncodingString = byteSequence.subSequence(5, 2).toHexString()
        val primaryEncodingType = magicType[primaryEncodingString]
            ?: throw java.lang.RuntimeException("Unknown primary encoding $primaryEncodingString.")

        if (beVerbose) println("Primary encoding ${primaryEncodingType.description}")

        val compressionEncoding = primaryEncodingType.compressionEncoding
        val compressionEncodingStart = primaryEncodingType.compressionEncodingStart
        val decoder = primaryEncodingType.serializationFormat.decoder
        return Triple(compressionEncoding, compressionEncodingStart, decoder.duplicate())
    }
}
