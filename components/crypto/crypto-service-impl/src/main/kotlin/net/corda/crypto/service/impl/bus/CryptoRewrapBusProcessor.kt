package net.corda.crypto.service.impl.bus

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics

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
            // Once rewrap is done, we can update state manager db
            val tenantIdWrappingKeysRecord = stateManager!!.get(listOf("kr${request.tenantId}"))
            require(tenantIdWrappingKeysRecord.size == 1)

            val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, UnmanagedKeyStatus::class.java)
            val serializer = cordaAvroSerializationFactory.createAvroSerializer<UnmanagedKeyStatus>()
            tenantIdWrappingKeysRecord.forEach { (key, state) ->
                val keyR = deserializer.deserialize(state.value)!!
                val newValue = serializer.serialize(UnmanagedKeyStatus(keyR.rootKeyAlias, keyR.total, keyR.rotatedKeys++))
                val failedToUpdate = stateManager.update(listOf(State(state.key, newValue!!, state.version + 1, state.metadata)))
                println("XXX: RewrapBusProcessor failed to update following states: ${failedToUpdate.keys}")
            }
        }
        return emptyList()
    }
}
