package net.corda.schema.registry.impl

import net.corda.data.AvroEnvelope
import net.corda.data.AvroGeneratedMessageClasses
import net.corda.data.Fingerprint
import net.corda.data.SchemaLoadException
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.debug
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.ByteArrays.toHexString
import org.apache.avro.Schema
import org.apache.avro.SchemaNormalization
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificData
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.specific.SpecificRecord
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Implementation of [AvroSchemaRegistry].
 */
@SuppressWarnings("TooManyFunctions")
@Component(service = [AvroSchemaRegistry::class])
class AvroSchemaRegistryImpl(
    private val options: Options = Options()
): AvroSchemaRegistry {

    /**
     * The set of options available for the [AvroSchemaRegistryImpl].
     */
    class Options(
        /**
         * If the serialized payload should be compressed.  When this option is set the serialized bytes of
         * an object will be compressed using Deflate.
         */
        val compressed: Boolean = false
    ) {
        // Just makes converting Boolean to a maskable Int prettier
        private fun Boolean.toInt() = compareTo(false)

        fun toFlags(): Int {
            return compressed.toInt()
        }

        companion object {
            private const val COMPRESSED_MASK = 0x1

            fun from(flags: Int): Options {
                return Options(
                    (flags and COMPRESSED_MASK) == COMPRESSED_MASK
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Contains the local class information for a given [SpecificRecord].  These records are based on
     * what has been compiled into the *local* classpath, and not necessarily what was used from
     * the other party.
     *
     * @param encoder The encoder used to serialize an outgoing message
     * @param decoder The decoder that will be used to deserialize an incoming message
     */
    private data class RecordData<T>(
        val encoder: (T) -> ByteArray,
        val decoder: (ByteArray, Schema, T?) -> T,
        val clazz: Class<out T>,
    )

    /**
     * A cache of the fingerprints we've generated for prepending to the messages.  Used to save
     * regenerating the fingerprint each time.
     */
    private val fingerprintsBySchema: ConcurrentHashMap<Schema, Fingerprint> =
        ConcurrentHashMap<Schema, Fingerprint>()
    private val fingerprintsByClazz: ConcurrentHashMap<Class<*>, Fingerprint> =
        ConcurrentHashMap<Class<*>, Fingerprint>()

    /**
     * Reverse mapping of [fingerprintsBySchema].
     */
    private val schemasByFingerprint: ConcurrentHashMap<Fingerprint, Schema> =
        ConcurrentHashMap<Fingerprint, Schema>()

    /**
     * Maps the things we'll need from a record by it's fingerprint.  Gives us the encoder/decoder on the class
     * as well as the reader schema for deserializing.
     */
    private val recordDataByFingerprint: ConcurrentHashMap<Fingerprint, RecordData<*>> =
        ConcurrentHashMap<Fingerprint, RecordData<*>>()

    private fun getFingerprint(schema: Schema): Fingerprint = fingerprintsBySchema[schema]
        ?: throw CordaRuntimeException("Could not find fingerprint for schema ${schema.fullName}")

    private fun getFingerprint(clazz: Class<*>): Fingerprint = fingerprintsByClazz[clazz]
        ?: throw CordaRuntimeException("Could not find fingerprint for class ${clazz.name}")

    private fun getSchema(fingerprint: Fingerprint) = schemasByFingerprint[fingerprint]
        ?: throw CordaRuntimeException("Could not find schema for fingerprint ${toHexString(fingerprint.bytes())}")

    private fun <T : Any> getRecordData(clazz: Class<T>) = recordDataByFingerprint[getFingerprint(clazz)]
        ?: throw CordaRuntimeException("Could not record data for class: $clazz")

    private fun getRecordData(fingerprint: Fingerprint) = recordDataByFingerprint[fingerprint]
        ?: throw CordaRuntimeException("Could not find data for record with fingerprint: $fingerprint")

    private fun <T : Any> getDecoder(clazz: Class<T>) = getRecordData(clazz).decoder
    private fun getDecoder(fingerprint: Fingerprint) = getRecordData(fingerprint).decoder
    @Suppress("unchecked_cast")
    private fun <T : Any> getEncoder(clazz: Class<T>): (T) -> ByteArray = (getRecordData(clazz).encoder) as (T) -> ByteArray
    private fun getEncoder(fingerprint: Fingerprint) = getRecordData(fingerprint).encoder

    /**
     * Special member function to install schemas.  Will create the encoder/decoder for each
     * class in [classes].
     *
     * Requires each class to have a static `getClassSchema(): Schema` method available which will return
     * an (Avro JSON Schema)[https://avro.apache.org/docs/current/gettingstartedjava.html#Defining+a+schema].
     *
     */
    fun <T : Any> initialiseSchemas(classes: Set<Class<out T>>) {
        classes.forEach {
            val getSchemaMethod = it.methods.find { method -> method.name == "getClassSchema" }
                ?: throw CordaRuntimeException("Could not find getClassSchema method for class ${it.name}")

            val schema = getSchemaMethod.invoke(null) as Schema

            val encoder: (T) -> ByteArray = { obj ->
                ByteArrayOutputStream().use { bytes ->
                    val binaryEncoder = EncoderFactory.get().binaryEncoder(bytes, null)
                    @Suppress("unchecked_cast")
                    val writer = SpecificDatumWriter(obj::class.java) as SpecificDatumWriter<T>
                    writer.write(obj, binaryEncoder)
                    binaryEncoder.flush()
                    bytes.toByteArray()
                }
            }

            val decoder: (ByteArray, Schema, T?) -> T = { bytes, writerSchema, obj ->
                val reader = SpecificDatumReader<T>(writerSchema, schema, SpecificData.getForClass(it))
                val binaryDecoder = DecoderFactory.get().binaryDecoder(bytes, null)
                reader.read(obj, binaryDecoder)
            }

            @Suppress("unchecked_cast")
            addSchema(schema, it as Class<T>, encoder, decoder)
        }
    }

    override fun <T : Any> addSchema(
        schema: Schema,
        clazz: Class<T>,
        encoder: (T) -> ByteArray,
        decoder: (ByteArray, Schema, T?) -> T
    ) = addSchemaLocal(schema, clazz, encoder, decoder)

    override fun addSchemaOnly(
        schema: Schema,
    ) = addSchemaLocal<Any>(schema, null, null, null)

    private fun <T : Any> addSchemaLocal(
        schema: Schema,
        clazz: Class<T>?,
        encoder: ((T) -> ByteArray)?,
        decoder: ((ByteArray, Schema, T?) -> T)?
    ) {
        log.debug { "Adding Schema: ${schema.fullName} for class $clazz" }
        // Quick exit before we do the heavy fingerprint operation
        if (!fingerprintsBySchema.containsKey(schema)) {
            val fingerprint = Fingerprint(SchemaNormalization.parsingFingerprint("SHA-256", schema))
            // Strictly not necessary to use `ifAbsent` but it's a bit of extra protection against exceptions
            // from dual adds.
            fingerprintsBySchema.putIfAbsent(schema, fingerprint)
            schemasByFingerprint.putIfAbsent(fingerprint, schema)
            if (clazz == null || encoder == null || decoder == null) {
                log.debug { "Skipping class type, encoder, and decoder registration as one or more values are missing." }
                return
            }
            fingerprintsByClazz.putIfAbsent(clazz, fingerprint)
            recordDataByFingerprint.putIfAbsent(
                fingerprint,
                RecordData(encoder, decoder, clazz)
            )
        }
    }

    override fun <T : Any> serialize(obj: T): ByteBuffer {
        log.trace("Serializing obj (${obj::class.java.name}): $obj")
        val fingerprint = getFingerprint(obj::class.java)
        @Suppress("unchecked_cast")
        val encoder: (T) -> ByteArray = getEncoder(obj::class.java) as (T) -> ByteArray
        val payload = try {
            if (options.compressed) {
                zipPayload(encoder.invoke(obj))
            } else {
                encoder.invoke(obj)
            }
        } catch (ex: Throwable) {
            log.error("Error invoking encoder serializing instance of class ${obj::class.java.name} with schema " +
                    "${fingerprint.schema}")
            throw ex
        }
        val envelope = AvroEnvelope(MAGIC, fingerprint, options.toFlags(), ByteBuffer.wrap(payload))
        return ByteBuffer.wrap(envelope.encode())
    }

    override fun <T : Any> deserialize(bytes: ByteBuffer, clazz: Class<T>, reusable: T?): T {
        return deserialize(bytes, 0, bytes.array().size - 1, clazz, reusable)
    }

    override fun <T : Any> deserialize(bytes: ByteBuffer, offset: Int, length: Int, clazz: Class<T>, reusable: T?): T {
        log.trace("Deserializing from: ${toHexString(bytes.array())}")
        val envelope = decodeAvroEnvelope(bytes.array())
        if (envelope.magic != MAGIC) {
            throw CordaRuntimeException("Incorrect Header detected.  Cannot deserialize message.")
        }

        val writerSchema = getSchema(envelope.fingerprint)
        @Suppress("unchecked_cast")
        val specificDecoder: (ByteArray, Schema, T?) -> T = getDecoder(clazz) as (ByteArray, Schema, T?) -> T
        val flags = Options.from(envelope.flags)
        val payload = if (flags.compressed) {
            unzipPayload(envelope.payload)
        } else {
            envelope.payload
        }
        return specificDecoder.invoke(payload.array(), writerSchema, reusable)
    }

    override fun getClassType(bytes: ByteBuffer): Class<*> {
        val envelope = decodeAvroEnvelope(bytes.array())
        return getRecordData(envelope.fingerprint).clazz
    }

    private fun AvroEnvelope.encode(): ByteArray {
        log.trace("Encoding envelope $this")
        return ByteArrayOutputStream().use {
            val binaryEncoder = EncoderFactory.get().binaryEncoder(it, null)
            @Suppress("unchecked_cast")
            val envelopeWriter = SpecificDatumWriter(this::class.java) as SpecificDatumWriter<AvroEnvelope>
            EncoderFactory.get().binaryEncoder(it, binaryEncoder)
            envelopeWriter.write(this, binaryEncoder)
            binaryEncoder.flush()
            it.toByteArray()
        }
    }

    private fun decodeAvroEnvelope(envelopeBytes: ByteArray, reuse: AvroEnvelope? = null): AvroEnvelope {
        val envelopeReader: SpecificDatumReader<AvroEnvelope> = SpecificDatumReader(AvroEnvelope.getClassSchema())
        val binaryDecoder = DecoderFactory.get().binaryDecoder(envelopeBytes, null)
        return envelopeReader.read(reuse, binaryDecoder)
    }

    private fun zipPayload(payload: ByteArray): ByteArray {
        log.debug { "Zipping payload" }
        val baos = ByteArrayOutputStream()
        DeflaterOutputStream(baos).use {
            it.write(payload)
            it.flush()
        }
        return baos.toByteArray()
    }

    private fun unzipPayload(payload: ByteBuffer): ByteBuffer {
        log.debug { "Unzipping payload" }
        val bytes = InflaterInputStream(payload.array().inputStream()).readAllBytes()
        return ByteBuffer.wrap(bytes)
    }

    init {
        val classes = try {
            AvroGeneratedMessageClasses.getAvroGeneratedMessageClasses()
        } catch (e: SchemaLoadException) {
            throw CordaRuntimeException("Initialization error in AvroSchemaRegistry", e)
        }

        initialiseSchemas(classes)
    }
}