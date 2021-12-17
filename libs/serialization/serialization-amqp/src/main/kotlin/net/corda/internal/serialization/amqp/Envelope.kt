package net.corda.internal.serialization.amqp

import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.Data
import org.apache.qpid.proton.codec.DescribedTypeConstructor

/**
 * This class wraps all serialized data, so that the schema can be carried along with it.  We will provide various
 * internal utilities to decompose and recompose with/without schema etc so that e.g. we can store objects with a
 * (relationally) normalised out schema to avoid excessive duplication.
 */
// TODO: make the schema parsing lazy since mostly schemas will have been seen before and we only need it if we
// TODO: don't recognise a type descriptor.
data class Envelope(val obj: Any?, val schema: Schema, val transformsSchema: TransformsSchema, val metadata: Metadata) : DescribedType {
    companion object : DescribedTypeConstructor<Envelope> {

        val DESCRIPTOR get() = AMQPDescriptorRegistry.ENVELOPE.amqpDescriptor

        val DESCRIPTOR_OBJECT get() = Descriptor(null, DESCRIPTOR)

        private val ENVELOPE_SIMPLE get() = 2
        private val ENVELOPE_WITH_TRANSFORMS get() = 3
        private val ENVELOPE_WITH_METADATA get() = 4

        private val BLOB_IDX get() = 0
        private val SCHEMA_IDX get() = 1
        private val TRANSFORMS_SCHEMA_IDX get() = 2
        private val METADATA_IDX get() = 3

        @Suppress("ThrowsCount")
        fun get(data: Data): Envelope {
            val describedType = data.`object` as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw AMQPNoTypeNotSerializableException(
                        "Unexpected descriptor ${describedType.descriptor}, should be $DESCRIPTOR.")
            }
            val list = describedType.described as List<*>

            // We need to cope with objects serialised without the transforms header and without metadata element in the envelope
            val transformSchema = when (list.size) {
                ENVELOPE_SIMPLE -> TransformsSchema.newInstance(null)
                ENVELOPE_WITH_TRANSFORMS, ENVELOPE_WITH_METADATA -> TransformsSchema.newInstance(list[TRANSFORMS_SCHEMA_IDX])
                else -> throw AMQPNoTypeNotSerializableException(
                        "Malformed list, bad length of ${list.size} (should be 2, 3 or 4)")
            }

            // We need to cope with objects serialised without metadata element in the envelope
            val metadata = when (list.size) {
                ENVELOPE_SIMPLE, ENVELOPE_WITH_TRANSFORMS -> Metadata()
                ENVELOPE_WITH_METADATA -> Metadata.get(list[METADATA_IDX]!!)
                else -> throw AMQPNoTypeNotSerializableException(
                        "Malformed list, bad length of ${list.size} (should be 2, 3 or 4)")
            }
            return newInstance(listOf(list[BLOB_IDX], Schema.get(list[SCHEMA_IDX]!!), transformSchema, metadata))
        }

        // This separation of functions is needed as this will be the entry point for the default
        // AMQP decoder if one is used (see the unit tests).
        @Suppress("ThrowsCount")
        override fun newInstance(described: Any?): Envelope {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")

            // We need to cope with objects serialised without the transforms header and without metadata element in the envelope
            val transformSchema = when (list.size) {
                ENVELOPE_SIMPLE -> TransformsSchema.newInstance(null)
                ENVELOPE_WITH_TRANSFORMS, ENVELOPE_WITH_METADATA -> list[TRANSFORMS_SCHEMA_IDX] as TransformsSchema
                else -> throw AMQPNoTypeNotSerializableException(
                        "Malformed list, bad length of ${list.size} (should be 2, 3 or 4)")
            }

            // We need to cope with objects serialised without metadata element in the envelope
            val metadata = when (list.size) {
                ENVELOPE_SIMPLE, ENVELOPE_WITH_TRANSFORMS -> Metadata()
                ENVELOPE_WITH_METADATA -> list[METADATA_IDX] as Metadata
                else -> throw AMQPNoTypeNotSerializableException(
                        "Malformed list, bad length of ${list.size} (should be 2, 3 or 4)")
            }
            return Envelope(list[BLOB_IDX], list[SCHEMA_IDX] as Schema, transformSchema, metadata)
        }

        override fun getTypeClass(): Class<*> = Envelope::class.java
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(obj, schema, transformsSchema, metadata)
}
