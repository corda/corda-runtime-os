package net.corda.testing.processors.db

import net.corda.data.AvroEnvelope
import net.corda.data.Fingerprint
import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.DeleteEntity
import net.corda.data.virtualnode.EntityRequest
import net.corda.data.virtualnode.MergeEntity
import net.corda.data.virtualnode.PersistEntity
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.schema.Schemas
import net.corda.schema.registry.impl.MAGIC
import net.corda.testing.bundles.cats.Cat
import net.corda.testing.bundles.dogs.Dog
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.apache.avro.Schema
import org.apache.avro.SchemaNormalization
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class DbSandbox {
    companion object {
        private val logger = contextLogger()
    }

    val holdingIdentity = HoldingIdentity(System.getProperty("x500"), System.getProperty("groupId"))

    @Test
    fun `persist cats and dogs`() {
        // key avro payload
        val key = holdingIdentity.toByteBuffer().let {
            val byteArray = ByteArray(it.capacity())
            it.get(byteArray)
            byteArray
        }

        val flowKey = FlowKey(UUID.randomUUID().toString(), holdingIdentity)

        // dog msg avro payload
        val dogBytes = this::class.java.getResource("/pluto-dog.amqp.bin").readBytes()
        val dogEntity = PersistEntity(ByteBuffer.wrap(dogBytes))
        val dogRequest = EntityRequest(Instant.now(), flowKey, dogEntity)
        val dogMsg = avroSerialize(ByteBuffer.wrap(encode(dogRequest)), EntityRequest.getClassSchema())

        // publish persist message
        val dogResponse = publishToKafka(key, dogMsg).get(1, TimeUnit.SECONDS)
        logger.info("Published on ${dogResponse.topic()} in offset ${dogResponse.offset()}")


        // cat msg avro payload
        val catBytes = this::class.java.getResource("/garfield-cat-plus-owner.amqp.bin").readBytes()
        val catEntity = PersistEntity(ByteBuffer.wrap(catBytes))
        val catRequest = EntityRequest(Instant.now(), flowKey, catEntity)
        val catMsg = avroSerialize(ByteBuffer.wrap(encode(catRequest)), EntityRequest.getClassSchema())

        // publish persist message
        val response = publishToKafka(key, catMsg).get(1, TimeUnit.SECONDS)
        logger.info("Published on ${response.topic()} in offset ${response.offset()}")
    }

    @Test
    fun `update dog`() {
        // key avro payload
        val key = holdingIdentity.toByteBuffer().let {
            val byteArray = ByteArray(it.capacity())
            it.get(byteArray)
            byteArray
        }

        // msg avro payload
        val dogBytes = this::class.java.getResource("/bella-dog.amqp.bin").readBytes()
        val entity = MergeEntity(ByteBuffer.wrap(dogBytes))
        val flowKey = FlowKey(UUID.randomUUID().toString(), holdingIdentity)
        val request = EntityRequest(Instant.now(), flowKey, entity)
        val msg = avroSerialize(ByteBuffer.wrap(encode(request)), EntityRequest.getClassSchema())

        // publish persist message
        val response = publishToKafka(key, msg).get(1, TimeUnit.SECONDS)
        logger.info("Published on ${response.topic()} in offset ${response.offset()}")
    }

    @Test
    fun `delete dog`() {
        // key avro payload
        val key = holdingIdentity.toByteBuffer().let {
            val byteArray = ByteArray(it.capacity())
            it.get(byteArray)
            byteArray
        }

        // msg avro payload
        val dogBytes = this::class.java.getResource("/bella-dog.amqp.bin").readBytes()
        val entity = DeleteEntity(ByteBuffer.wrap(dogBytes))
        val flowKey = FlowKey(UUID.randomUUID().toString(), holdingIdentity)
        val request = EntityRequest(Instant.now(), flowKey, entity)
        val msg = avroSerialize(ByteBuffer.wrap(encode(request)), EntityRequest.getClassSchema())

        // publish delete message
        val response = publishToKafka(key, msg).get(1, TimeUnit.SECONDS)
        logger.info("Published on ${response.topic()} in offset ${response.offset()}")
    }

    // Run as a one-off, until we support schema migration as part of CPK install.
    @Test
    fun `migrate schema`() {
        var lbm = LiquibaseSchemaMigratorImpl()
        // TODO: find Vnode schema name dynamically
        val vnodeSchema = "vnode_vault_48666eb5b396"
        val jdbcUrl = "jdbc:postgresql://prereqs-postgresql.corda:5432/cordacluster?currentSchema=$vnodeSchema"
        // TODO: set username/password dynamically
        val ds = PostgresDataSourceFactory().create(
            jdbcUrl, "user", "pass")

        val changelog = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    Dog::class.java.packageName, listOf(
                        "migration/cats-migration-v1.0.xml",
                        "migration/owner-migration-v1.0.xml",
                        "migration/dogs-migration-v1.0.xml")
                )
            )
        )
        lbm.updateDb(ds.connection, changelog)
    }

    val producerProperties =
        mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to System.getProperty("kafkaBrokerAddress"),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java.name,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java.name,
            ProducerConfig.MAX_BLOCK_MS_CONFIG to "1500",
        ).toProperties()

    fun publishToKafka(key: ByteArray?, value: ByteArray?): Future<RecordMetadata> {
        logger.info("Create Kafka producer with: $producerProperties")

        return KafkaProducer<ByteArray, ByteArray>(producerProperties).use {
            it.send(ProducerRecord(
                Schemas.VirtualNode.ENTITY_PROCESSOR,
                key,
                value))
        }
    }

    // copy/paste from the CordaAvroSerializationFactory
    //   These are generic enough they could be in a non-OSGI module.
    private fun avroSerialize(payload: ByteBuffer, schema: Schema): ByteArray {
        val fingerprint = Fingerprint(SchemaNormalization.parsingFingerprint("SHA-256", schema))
        val envelope = AvroEnvelope(MAGIC, fingerprint, 0, payload)
        return encode(envelope)
    }

    private fun <T: Any> encode(obj: T): ByteArray {
        return ByteArrayOutputStream().use { bytes ->
            val binaryEncoder = EncoderFactory.get().binaryEncoder(bytes, null)
            val writer: SpecificDatumWriter<T> = uncheckedCast(SpecificDatumWriter(obj::class.java))
            writer.write(obj, binaryEncoder)
            binaryEncoder.flush()
            bytes.toByteArray()
        }
    }
}