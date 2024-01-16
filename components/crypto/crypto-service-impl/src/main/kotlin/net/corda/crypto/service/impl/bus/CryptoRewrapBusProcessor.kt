package net.corda.crypto.service.impl.bus

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import org.slf4j.LoggerFactory
import kotlin.random.Random

private const val REWRAP_KEYS_OPERATION_NAME = "rewrapKeys"

/**
 * This processor does actual re-wrapping of the keys.
 * Each Kafka message contains information about one key that needs to be re-wrapped.
 */
@Suppress("LongParameterList")
class CryptoRewrapBusProcessor(
    val cryptoService: CryptoService,
    private val stateManager: StateManager?,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : DurableProcessor<String, IndividualKeyRotationRequest> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = IndividualKeyRotationRequest::class.java
    private val rewrapTimer = CordaMetrics.Metric.Crypto.RewrapKeysTimer.builder()
        .withTag(CordaMetrics.Tag.OperationName, REWRAP_KEYS_OPERATION_NAME)
        .build()

    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, UnmanagedKeyStatus::class.java)
    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<UnmanagedKeyStatus>()

    override fun onNext(events: List<Record<String, IndividualKeyRotationRequest>>): List<Record<*, *>> {
        events.mapNotNull { it.value }.map { request ->
            rewrapTimer.recordCallable {
                cryptoService.rewrapWrappingKey(request.tenantId, request.targetKeyAlias, request.newParentKeyAlias)
            }

            // Once re-wrap is done, we can update the state manager
            var statusUpdated = false
            while (!statusUpdated) {
                // rootKeyAlias + tenantId + keyRotation is the unique key, therefore we don't need to do the table
                // search through state manager
                val tenantIdWrappingKeysRecords =
                    stateManager!!.get(listOf(request.oldParentKeyAlias + request.tenantId + "keyRotation"))
                require(tenantIdWrappingKeysRecords.size == 1) { "Found none or more than 1 ${request.tenantId} record " +
                        "in the database for rootKeyAlias ${request.oldParentKeyAlias}. Found records $tenantIdWrappingKeysRecords." }

                tenantIdWrappingKeysRecords.forEach { (_, state) ->
                    logger.debug("Updating state manager record for tenantId ${state.metadata["tenantId"]} " +
                            "after re-wrapping ${request.targetKeyAlias}.")
                    val deserializedStatus = deserializer.deserialize(state.value)!!
                    val newValue =
                        serializer.serialize(
                            UnmanagedKeyStatus(
                                deserializedStatus.rootKeyAlias,
                                deserializedStatus.total,
                                deserializedStatus.rotatedKeys + 1
                            )
                        )
                    // Update status to Done if all keys for the tenant have been rotated
                    val newMetadata = if (deserializedStatus.total == deserializedStatus.rotatedKeys + 1) {
                        updateMetadata(state.metadata, "status", "Done")
                    } else {
                        state.metadata
                    }
                    val failedToUpdate =
                        stateManager.update(listOf(State(state.key, newValue!!, state.version, newMetadata)))
                    if (failedToUpdate.isNotEmpty()) {
                        logger.debug("Failed to update following states ${failedToUpdate.keys}, retrying.")
                        Thread.sleep(Random.nextLong(0, 1000))
                    } else {
                        statusUpdated = true
                    }
                }
            }
        }
        return emptyList()
    }

    private fun updateMetadata(metadata: Metadata, key: String, value: String): Metadata {
        val newMetadata = mutableMapOf<String, String>()
        metadata.keys.filter { it != key }
            .forEach { metadataKey ->
                newMetadata[metadataKey] = metadata[metadataKey].toString()
            }
        newMetadata[key] = value
        return Metadata(newMetadata)
    }
}
