package net.corda.crypto.service.impl.bus


import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.KeyRotationMetadataValues
import net.corda.crypto.core.KeyRotationRecordType
import net.corda.crypto.core.KeyRotationStatus
import net.corda.crypto.core.getKeyRotationStatusRecordKey
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Crypto.REWRAP_MESSAGE_TOPIC
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * This processor goes through the databases and find out what keys need re-wrapping.
 * It then posts a message to Kafka for each key needing re-wrapping with the tenant ID.
 */
@Suppress("LongParameterList")
class CryptoRekeyBusProcessor(
    val cryptoService: CryptoService,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val wrappingRepositoryFactory: WrappingRepositoryFactory,
    private val rekeyPublisher: Publisher,
    private val stateManagerInit: StateManager?,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : DurableProcessor<String, KeyRotationRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = KeyRotationRequest::class.java
    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<UnmanagedKeyStatus>()
    private val stateManager: StateManager
        get() = checkNotNull(stateManagerInit) {
            "State manager for key rotation is not initialised."
        }

    @Suppress("NestedBlockDepth")
    override fun onNext(events: List<Record<String, KeyRotationRequest>>): List<Record<*, *>> {
        logger.debug("received ${events.size} key rotation requests")

        events.mapNotNull { it.timestamp to it.value }.forEach { (timestamp, request) ->
            logger.debug("processing $request")
            require(request != null)

            // Check if there is a running key rotation for oldParentKeyAlias.
            // Same check is done on the Rest worker side, but if user quickly issues two key rotation commands after each other,
            // it will pass rest worker check as state manager was not yet populated.
            if (!hasPreviousRotationFinished(request.oldParentKeyAlias)) {
                logger.error(
                    "There is already a key rotation of unmanaged wrapping key " +
                        "with alias ${request.oldParentKeyAlias} in progress."
                )
                return emptyList()
            }

            // Root (unmanaged) keys can be used in clusterDB and vNodeDB. We need to go through all tenants and
            // clusterDB, and check if the oldKeyAlias is used there. If yes, we will issue a new record for this key
            // to be re-wrapped.

            // tenantId in the request is useful ONLY for re-wrapping managed keys, so we ignore it here.

            val virtualNodeInfo = virtualNodeInfoReadService.getAll() // Get all the virtual nodes
            val virtualNodeTenantIds = virtualNodeInfo.map { it.holdingIdentity.shortHash.toString() }

            // We do not need to use separate wrapping repositories for the different cluster level tenants,
            // since they share the cluster crypto database. So we scan over the virtual node tenants and an arbitrary
            // choice of cluster level tenant. We pick CryptoTenants.CRYPTO as the arbitrary cluster level tenant,
            // and we should not also check CryptoTenants.P2P and CryptoTenants.REST since if we do we'll get duplicate.
            val allTenantIds = virtualNodeTenantIds + listOf(CryptoTenants.CRYPTO)
            logger.debug("Found ${allTenantIds.size} tenants; first few are: ${allTenantIds.take(10)}")
            val targetWrappingKeys = allTenantIds.asSequence().map { tenantId ->
                wrappingRepositoryFactory.create(tenantId).use { wrappingRepo ->
                    wrappingRepo.findKeysWrappedByParentKey(request.oldParentKeyAlias).map { wki -> tenantId to wki }
                }
            }.flatten()

            // First update state manager, then publish re-wrap messages, so the state manager db is already populated
            val records = mutableListOf<State>()

            // Group by tenantId/vNode
            targetWrappingKeys.groupBy { it.first }.forEach { (tenantId, wrappingKeys) ->
                logger.debug("Grouping wrapping keys by vNode/tenantId $tenantId")
                val status = UnmanagedKeyStatus(request.oldParentKeyAlias, wrappingKeys.size, 0)
                records.add(
                    State(
                        // key is set as a unique string to prevent table search in re-wrap bus processor
                        getKeyRotationStatusRecordKey(request.oldParentKeyAlias, tenantId),
                        checkNotNull(serializer.serialize(status)),
                        1,
                        Metadata(
                            mapOf(
                                KeyRotationMetadataValues.ROOT_KEY_ALIAS to request.oldParentKeyAlias,
                                KeyRotationMetadataValues.TENANT_ID to tenantId,
                                KeyRotationMetadataValues.TYPE to KeyRotationRecordType.KEY_ROTATION,
                                KeyRotationMetadataValues.STATUS to KeyRotationStatus.IN_PROGRESS,
                                STATE_TYPE to status::class.java.name
                            )
                        )
                    )
                )
            }

            // Only delete previous key rotation status if we are actually going to rotate something
            // If we can't delete previous records, we won't start new key rotation
            try {
                if (records.isNotEmpty()) deleteStateManagerRecords(request.oldParentKeyAlias)
            } catch (e: IllegalStateException) {
                logger.error(
                    "Unable to delete previous key rotation records. " +
                        "Cannot start new key rotation for ${request.oldParentKeyAlias}."
                )
                return emptyList()
            }
            stateManager.create(records)

            rekeyPublisher.publish(
                targetWrappingKeys.map { (tenantId, wrappingKeyInfo) ->
                    Record(
                        REWRAP_MESSAGE_TOPIC,
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(
                            request.requestId,
                            tenantId,
                            request.oldParentKeyAlias,
                            request.newParentKeyAlias,
                            wrappingKeyInfo.alias,
                            null, // keyUuid not used in unmanaged key rotation
                            KeyType.UNMANAGED
                        ),
                        timestamp // This is useful, if we are keeping the track of start time
                    )
                }.toList()
            )
        }

        return emptyList()
    }

    private fun hasPreviousRotationFinished(oldKeyAlias: String): Boolean {
        stateManager.findByMetadataMatchingAll(
            listOf(
                MetadataFilter(KeyRotationMetadataValues.ROOT_KEY_ALIAS, Operation.Equals, oldKeyAlias),
                MetadataFilter(KeyRotationMetadataValues.TYPE, Operation.Equals, KeyRotationRecordType.KEY_ROTATION)
            )
        ).forEach {
            if (it.value.metadata[KeyRotationMetadataValues.STATUS] != KeyRotationStatus.DONE) return false
        }
        return true
    }

    private fun deleteStateManagerRecords(oldParentKeyAlias: String) {
        var recordsDeleted = false
        var retries = 10
        while (!recordsDeleted) {
            if (retries == 0) throw IllegalStateException("Cannot delete previous key rotation records. Cannot proceed with key rotation.")
            val toDelete = stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter(KeyRotationMetadataValues.ROOT_KEY_ALIAS, Operation.Equals, oldParentKeyAlias),
                    MetadataFilter(KeyRotationMetadataValues.TYPE, Operation.Equals, KeyRotationRecordType.KEY_ROTATION)
                )
            )
            logger.info("Deleting following records ${toDelete.keys} for previous key rotation for rootKeyAlias $oldParentKeyAlias.")
            val failedToDelete = stateManager.delete(toDelete.values)
            if (failedToDelete.isNotEmpty()) {
                logger.warn(
                    "Failed to delete following states " +
                        "${failedToDelete.keys} from the state manager for rootKeyAlias $oldParentKeyAlias, retrying."
                )
                retries--
            } else {
                recordsDeleted = true
            }
        }
    }
}
