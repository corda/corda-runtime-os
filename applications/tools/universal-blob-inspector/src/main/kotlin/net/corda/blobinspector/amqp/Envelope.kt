package net.corda.blobinspector.amqp

import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.Data
import org.apache.qpid.proton.codec.DescribedTypeConstructor

data class Envelope(val obj: Any?, val schema: Schema, val transformsSchema: Any?, val bundles: Any?) : DescribedType {
    companion object : DescribedTypeConstructor<Envelope> {
        val DESCRIPTOR = PredefinedDescriptorRegistry.ENVELOPE.amqpDescriptor
        // val DESCRIPTOR_OBJECT = Descriptor(null, DESCRIPTOR)

        // described list should either be two or three elements long
        private const val ENVELOPE_WITHOUT_TRANSFORMS = 2
        private const val ENVELOPE_WITH_TRANSFORMS = 3
        private const val ENVELOPE_WITH_BUNDLES = 4

        private const val BLOB_IDX = 0
        private const val SCHEMA_IDX = 1
        private const val TRANSFORMS_SCHEMA_IDX = 2
        private const val BUNDLES_IDX = 3

        @Suppress("ThrowsCount")
        fun get(data: Data, osgiBundles: Boolean = false): Envelope {
            val describedType = data.describedType
            if (describedType.descriptor != DESCRIPTOR) {
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException("Unexpected descriptor ${describedType.descriptor}, should be $DESCRIPTOR.")
            }
            val list = describedType.described as List<*>

            // We need to cope with objects serialised without the transforms header element in the
            // envelope
            val transformSchema: Any? = when (list.size) {
                ENVELOPE_WITHOUT_TRANSFORMS -> null
                ENVELOPE_WITH_TRANSFORMS -> list[TRANSFORMS_SCHEMA_IDX]
                ENVELOPE_WITH_BUNDLES -> if (!osgiBundles) {
                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException(
                        "Malformed list, bad length of ${list.size} (should be 2 or 3)"
                    )
                } else {
                    list[TRANSFORMS_SCHEMA_IDX]
                }
                else -> {
                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException("Malformed list, bad length of ${list.size} (should be 2 or 3)")
                }
            }

            val bundles: Any? = when (list.size) {
                ENVELOPE_WITH_BUNDLES -> list[BUNDLES_IDX]
                else -> null
            }
            return newInstance(listOf(list[BLOB_IDX], Schema.get(list[SCHEMA_IDX]!!), transformSchema, bundles))
        }

        // This separation of functions is needed as this will be the entry point for the default
        // AMQP decoder if one is used (see the unit tests).
        override fun newInstance(described: Any?): Envelope {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")

            // We need to cope with objects serialised without the transforms header element in the
            // envelope
            val transformSchema: Any? = if (list.size >= ENVELOPE_WITH_TRANSFORMS) {
                list[TRANSFORMS_SCHEMA_IDX]
            } else {
                null
            }
            val bundles: Any? = if (list.size >= ENVELOPE_WITH_BUNDLES) {
                list[BUNDLES_IDX]
            } else {
                null
            }
            return Envelope(list[BLOB_IDX], list[SCHEMA_IDX] as Schema, transformSchema, bundles)
        }

        override fun getTypeClass(): Class<*> = Envelope::class.java
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(obj, schema, transformsSchema)
}
