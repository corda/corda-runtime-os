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
    override val keyClass: Class<String> = String::class.java
    override val valueClass = IndividualKeyRotationRequest::class.java
    private val rewrapTimer = CordaMetrics.Metric.Crypto.RewrapKeysTimer.builder()
        .withTag(CordaMetrics.Tag.OperationName, REWRAP_KEYS_OPERATION_NAME)
        .build()

    override fun onNext(events: List<Record<String, IndividualKeyRotationRequest>>): List<Record<*, *>> {
        events.mapNotNull { it.value }.map { request ->
            rewrapTimer.recordCallable {
                cryptoService.rewrapWrappingKey(request.tenantId, request.targetKeyAlias, request.newParentKeyAlias)
            }

            val deserializer =
                cordaAvroSerializationFactory.createAvroDeserializer({}, UnmanagedKeyStatus::class.java)
            val serializer = cordaAvroSerializationFactory.createAvroSerializer<UnmanagedKeyStatus>()

            // Once rewrap is done, we can update state manager db
            var statusUpdated = false
            while (!statusUpdated) {
                // rootKeyAlias + tenantId + keyRotation is the unique key, therefore we don't need to do the table search through state manager db
                val tenantIdWrappingKeysRecords =
                    stateManager!!.get(listOf(request.oldParentKeyAlias + request.tenantId + "keyRotation"))  // rootKeyAlias + tenantId + keyRotation
                require(tenantIdWrappingKeysRecords.size == 1) { "Found more than 1 ${request.tenantId} records in the database for rootKeyAlias = ${request.oldParentKeyAlias}." }

                tenantIdWrappingKeysRecords.forEach { (key, state) ->
                    println("XXX: dealing with tenantId: $key, wrapping key: ${request.targetKeyAlias}")
                    val deserializedStatus = deserializer.deserialize(state.value)!!
                    val newValue =
                        serializer.serialize(
                            UnmanagedKeyStatus(
                                deserializedStatus.rootKeyAlias,
                                deserializedStatus.total,
                                deserializedStatus.rotatedKeys + 1
                            )
                        )
                    // we want to update status to Done if all keys for the tenant have been rotated
                    val newMetadata = if (deserializedStatus.total == deserializedStatus.rotatedKeys + 1) {
                        updateMetadata(state.metadata, "status", "Done")
                    } else {
                        state.metadata
                    }
                    val failedToUpdate =
                        stateManager.update(listOf(State(key, newValue!!, state.version, newMetadata)))
                    if (failedToUpdate.isNotEmpty()) {
                        println("XXX: RewrapBusProcessor failed to update following states: ${failedToUpdate.keys}.")
                        Thread.sleep(Random.nextLong(0, 1000))
                    } else {
                        println("job done")
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
