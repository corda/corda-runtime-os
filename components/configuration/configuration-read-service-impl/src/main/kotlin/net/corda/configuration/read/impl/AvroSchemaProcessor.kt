package net.corda.configuration.read.impl

import net.corda.data.Fingerprint
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.AvroSchema.AVRO_SCHEMA_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.avro.Schema

import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class AvroSchemaProcessor(
    private val coordinator: LifecycleCoordinator,
    private val avroSchemaRegistry: AvroSchemaRegistry
) : CompactedProcessor<Fingerprint, String> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val PUBLISH_TIMEOUT_SECONDS = 5L
    }

    override val keyClass: Class<Fingerprint>
        get() = Fingerprint::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    private val recordsToWrite = mutableListOf<Record<Fingerprint, String>>()
    private var readSchemas = false

    override fun onSnapshot(currentData: Map<Fingerprint, String>) {
        logger.info("Received ${currentData.size} Avro schemas from topic in initial snapshot.")

        // Read external schemas
        currentData
            .filterKeys { fingerprint ->
                !avroSchemaRegistry.containsSchema(fingerprint)
            }
            .values.forEach { schemaJson ->
                val schema = Schema.Parser().parse(schemaJson)
                avroSchemaRegistry.addSchemaOnly(schema)
            }

        val schemasMissingFromSnapshot =
            avroSchemaRegistry.schemasByFingerprintSnapshot.filterKeys { !currentData.containsKey(it) }

        // Record any schemas we know about that aren't already in the topic snapshot
        recordsToWrite.addAll(schemasMissingFromSnapshot.map { Record(AVRO_SCHEMA_TOPIC, it.key, it.value.toString()) })
        if (recordsToWrite.isNotEmpty()) {
            logger.info("Queued ${recordsToWrite.size} Avro schemas to write to topic.")
        }

        readSchemas = true
        coordinator.postEvent(SetupConfigSubscription())
    }

    override fun onNext(
        newRecord: Record<Fingerprint, String>,
        oldValue: String?,
        currentData: Map<Fingerprint, String>
    ) {
        avroSchemaRegistry.addSchemaOnly(Schema.Parser().parse(newRecord.value))
    }

    fun publishNewSchemas(publisher: Publisher) {
        if (!readSchemas) {
            logger.info("Attempt to publish new schemas made prior to reading.")
            return
        }

        if (recordsToWrite.isNotEmpty()) {
            logger.info("Publishing ${recordsToWrite.size} queued Avro schemas to topic.")

            val futures = publisher.publish(recordsToWrite)
            try {
                @Suppress("SpreadOperator")
                CompletableFuture.allOf(*futures.toTypedArray()).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                // The consequence of this is some current version schemas may not be available to later versions of the
                // software. All workers will attempt to publish missing schemas so there is every change this would
                // repair itself. If it didn't future versions of software might reject some evolved avro messages as it
                // not know what they are.
                logger.info("Timeout waiting for ${recordsToWrite.size} schemas to publish on bootstrapping.")
            }
            recordsToWrite.clear()

            logger.info("Finished publishing queued Avro schemas to topic.")
        }
    }
}
