package net.corda.crypto.service.impl.bus

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.KeyRotationMetadataValues
import net.corda.crypto.core.KeyRotationStatus
import net.corda.crypto.core.getKeyRotationStatusRecordKey
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.ManagedKeyStatus
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import org.slf4j.LoggerFactory
import java.util.UUID

private const val REWRAP_KEYS_OPERATION_NAME = "rewrapKeys"

/**
 * This processor does actual re-wrapping of the keys.
 * Each Kafka message contains information about one key that needs to be re-wrapped.
 */
@Suppress("LongParameterList")
class CryptoRewrapBusProcessor(
    val cryptoService: CryptoService,
    private val stateManager: StateManager,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val defaultUnmanagedWrappingKeyName: String,
) : DurableProcessor<String, IndividualKeyRotationRequest> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = IndividualKeyRotationRequest::class.java
    private val rewrapTimer = CordaMetrics.Metric.Crypto.RewrapKeysTimer.builder()
        .withTag(CordaMetrics.Tag.OperationName, REWRAP_KEYS_OPERATION_NAME)
        .build()

    private val unmanagedDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, UnmanagedKeyStatus::class.java)
    private val unmanagedSerializer = cordaAvroSerializationFactory.createAvroSerializer<UnmanagedKeyStatus>()

    private val managedDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, ManagedKeyStatus::class.java)
    private val managedSerializer = cordaAvroSerializationFactory.createAvroSerializer<ManagedKeyStatus>()

    override fun onNext(events: List<Record<String, IndividualKeyRotationRequest>>): List<Record<*, *>> {
        events.mapNotNull { it.value }.map { request ->
            try {
                processEvent(request)
            } catch (ex: Exception) {
                logger.warn("A IndividualKeyRotationRequest event could not be processed:", ex)
            }
        }
        return emptyList()
    }

    private fun CryptoRewrapBusProcessor.processEvent(request: IndividualKeyRotationRequest) {
        checkNotNull(stateManager) { "State manager is not initialised, cannot proceed with key rotation." }
        if (request.tenantId.isNullOrEmpty()) {
            logger.info("tenantId missing from IndividualKeyRotationRequest type:${request.keyType}, ignoring.")
            return
        }

        when (request.keyType) {
            KeyType.UNMANAGED -> {
                if (request.targetKeyAlias.isNullOrEmpty()) {
                    logger.info("targetKeyAlias missing from unmanaged IndividualKeyRotationRequest, ignoring.")
                    return
                }
                if (request.keyUuid != null) {
                    logger.info("keyUuid provided for unmanaged IndividualKeyRotationRequest, ignoring.")
                    return
                }

                rewrapTimer.recordCallable {
                    cryptoService.rewrapWrappingKey(
                        request.tenantId,
                        request.targetKeyAlias,
                        defaultUnmanagedWrappingKeyName
                    )
                }

                writeStateForUnmanagedKey(stateManager, request)
            }

            KeyType.MANAGED -> {
                if (request.targetKeyAlias != null) {
                    logger.info("targetKeyAlias provided for managed IndividualKeyRotationRequest, ignoring.")
                    return
                }
                if (request.keyUuid.isNullOrEmpty()) {
                    logger.info("keyUuid missing from managed IndividualKeyRotationRequest, ignoring.")
                    return
                }
                val uuid = try {
                    UUID.fromString(request.keyUuid)
                } catch (ex: IllegalArgumentException) {
                    logger.info("Invalid keyUuid from unmanaged IndividualKeyRotationRequest, ignoring.")
                    return
                }

                val rewrappedKeys = rewrapTimer.recordCallable {
                    cryptoService.rewrapAllSigningKeysWrappedBy(uuid, request.tenantId)
                }

                writeStateForManagedKey(stateManager, request, rewrappedKeys!!)
            }

            else -> logger.info("Invalid IndividualKeyRotationRequest message, ignoring.")
        }
    }

    private fun writeStateForManagedKey(
        stateManager: StateManager,
        request: IndividualKeyRotationRequest,
        rewrappedKeys: Int
    ) {
        // Once re-wrap is done, we can update the state manager
        var statusUpdated = false

        while (!statusUpdated) {
            // we defined the key to be unique to avoid table search through state manager
            val tenantIdSigningKeysRecords =
                stateManager.get(
                    listOf(
                        getKeyRotationStatusRecordKey(
                            request.keyUuid,
                            request.tenantId
                        )
                    )
                )
            check(tenantIdSigningKeysRecords.size == 1) {
                "Found none or more than 1 ${request.tenantId} record " +
                        "in the database for rootKeyAlias ${request.keyUuid}. Found records $tenantIdSigningKeysRecords."
            }

            tenantIdSigningKeysRecords.entries.single().let { (_, state) ->
                logger.debug(
                    "Updating state manager record for tenantId ${request.tenantId} " +
                            "after re-wrapping ${request.keyUuid}."
                )
                val deserializedStatus = checkNotNull(managedDeserializer.deserialize(state.value))
                val newValue =
                    checkNotNull(
                        managedSerializer.serialize(
                            ManagedKeyStatus(
                                deserializedStatus.wrappingKeyAlias,
                                deserializedStatus.total,
                                deserializedStatus.rotatedKeys + rewrappedKeys,
                                deserializedStatus.createdTimestamp
                            )
                        )
                    )
                // Update status to Done if all keys for the tenant have been rotated
                val newMetadata = getNewMetadata(
                    deserializedStatus.total,
                    deserializedStatus.rotatedKeys + rewrappedKeys,
                    state)
                statusUpdated = updateState(stateManager, state, newValue, newMetadata)
            }
        }

        logger.debug("Update state manager ${stateManager.name} for managed key rotation tenantId: ${request.tenantId}.")
    }

    private fun writeStateForUnmanagedKey(
        stateManager: StateManager,
        request: IndividualKeyRotationRequest
    ) {
        // Once re-wrap is done, we can update the state manager
        var statusUpdated = false

        while (!statusUpdated) {
            // we defined the key to be unique to avoid table search through state manager
            val tenantIdWrappingKeysRecords =
                stateManager.get(
                    listOf(
                        getKeyRotationStatusRecordKey(
                            defaultUnmanagedWrappingKeyName,
                            request.tenantId
                        )
                    )
                )
            check(tenantIdWrappingKeysRecords.size == 1) {
                "Found none or more than 1 ${request.tenantId} record " +
                        "in the database for new master wrapping key $defaultUnmanagedWrappingKeyName. " +
                        "Found records $tenantIdWrappingKeysRecords."
            }

            tenantIdWrappingKeysRecords.forEach { (_, state) ->
                logger.debug(
                    "Updating state manager record for tenantId ${request.tenantId} " +
                        "after re-wrapping ${request.targetKeyAlias}."
                )
                val deserializedStatus = checkNotNull(unmanagedDeserializer.deserialize(state.value))
                val newValue =
                    checkNotNull(
                        unmanagedSerializer.serialize(
                            UnmanagedKeyStatus(
                                deserializedStatus.tenantId,
                                deserializedStatus.total,
                                deserializedStatus.rotatedKeys + 1,
                                deserializedStatus.createdTimestamp
                            )
                        )
                    )
                // Update status to Done if all keys for the tenant have been rotated
                val newMetadata = getNewMetadata(
                    deserializedStatus.total,
                    deserializedStatus.rotatedKeys + 1,
                    state)
                statusUpdated = updateState(stateManager, state, newValue, newMetadata)
            }
        }
    }

    private fun mergeMetadata(existing: Metadata?, newMetadata: Metadata?, stateType: String): Metadata {
        val map = (existing ?: metadata()).toMutableMap()
        newMetadata?.forEach { map[it.key] = it.value }
        map[STATE_TYPE] = stateType

        return Metadata(map)
    }

    private fun getNewMetadata(total: Int, rotatedKeys: Int, state: State): Metadata =
        if (total == rotatedKeys) {
            mergeMetadata(
                state.metadata,
                Metadata(mapOf(KeyRotationMetadataValues.STATUS to KeyRotationStatus.DONE)),
                state.metadata[STATE_TYPE].toString()
            )
        } else {
            state.metadata
        }

    private fun updateState(
        stateManager: StateManager,
        state: State,
        newValue: ByteArray,
        newMetadata: Metadata
    ): Boolean {
        val failedToUpdate =
            stateManager.update(listOf(State(state.key, newValue, state.version, newMetadata)))
        if (failedToUpdate.isNotEmpty()) {
            logger.debug("Failed to update following states ${failedToUpdate.keys}, retrying.")
            return false
        }
        return true
    }
}
