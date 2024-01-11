package net.corda.crypto.service.impl.bus


import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
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
    private val stateManager: StateManager?,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : DurableProcessor<String, KeyRotationRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = KeyRotationRequest::class.java

    @Suppress("NestedBlockDepth")
    override fun onNext(events: List<Record<String, KeyRotationRequest>>): List<Record<*, *>> {
        logger.debug("received ${events.size} key rotation requests")

        // should serializer be instantiated in the companion object as a static object for the whole class?
        val serializer = cordaAvroSerializationFactory.createAvroSerializer<UnmanagedKeyStatus>()

        events.mapNotNull { it.timestamp to it.value }.forEach { (timestamp, request) ->
            logger.debug("processing $request")
            require(request != null)

            // Check first if there is a running key rotation for oldParentKeyAlias.
            // Same check is done on the Rest worker side, but if user quickly issues two key rotation commands after each other,
            // it will pass rest worker check as state manager was not yet populated.
            if (!hasPreviousRotationFinished(request.oldParentKeyAlias)) {
                logger.error("There is already a key rotation of unmanaged wrapping key with alias ${request.oldParentKeyAlias} in progress.")
                return emptyList()
            }

            // root (unmanaged) keys can be used in clusterDB and vNodeDB
            // then for each key we will send a record on Kafka
            // We do not have a code that deals with rewrapping the root key in a cluster DB

            // tenantId in the request is useful ONLY for re-wrapping managed keys, which is not yet implemented,
            // so we ignore it.
            //
            // For unmanaged (root) key we need to go through all tenants, i.e. all vNodes and some others,
            // and check if the oldKeyAlias is used there  if yes, then we need to issue a new record for this key
            // to be re-wrapped

            val virtualNodeInfo = virtualNodeInfoReadService.getAll() // Get all the virtual nodes
            val virtualNodeTenantIds = virtualNodeInfo.map { it.holdingIdentity.shortHash.toString() }

            // we do not need to use separate wrapping repositories for the different cluster level tenants,
            // since they share the cluster crypto database. So we scan over the virtual node tenants and an arbitrary
            // choice of cluster level tenant. We pick CryptoTenants.CRYPTO as the arbitrary cluster level tenant,
            // and we should not also check CryptoTenants.P2P and CryptoTenants.REST since if we do we'll get duplicate.
            val allTenantIds = virtualNodeTenantIds + listOf(CryptoTenants.CRYPTO)
            logger.debug("Found ${allTenantIds.size} tenants; first few are: ${allTenantIds.take(10)}")
            val targetWrappingKeys = allTenantIds.asSequence().map { tenantId ->
                wrappingRepositoryFactory.create(tenantId).use { wrappingRepo ->
                    wrappingRepo.findKeysWrappedByAlias(request.oldParentKeyAlias).map { wki -> tenantId to wki }
                }
            }.flatten()

            // First update state manager, then publish rewrap messages, so the state manager db is already populated
            val records = mutableListOf<State>()

            // Group by tenantId/vNode
            targetWrappingKeys.groupBy { it.first }.forEach {
                logger.info("XXX: Grouping wrapping keys by vNode/tenantId ${it.key}")
                println("XXX: Grouping wrapping keys by vNode/tenantId ${it.key}")
                val status = UnmanagedKeyStatus(request.oldParentKeyAlias, it.value.size, 0)
                records.add(
                    State(
                        UUID.randomUUID().toString(),
                        serializer.serialize(status)!!,
                        1,
                        Metadata(
                            mapOf(
                                "rootKeyAlias" to request.oldParentKeyAlias,
                                "tenantId" to it.key,
                                "type" to "keyRotation", // maybe create an enum from type, so we can easily add more if needed
                                "status" to "inProgress",
                                STATE_TYPE to status::class.java.name
                            )
                        )
                    )
                )
            }

            // Only delete previous key rotation status if we are actually going to rotate something
            if (records.isNotEmpty()) {
                deleteStateManagerRecords(request.oldParentKeyAlias)
                logger.info("XXX: Storing wrapping keys grouped by tenantId into state manager db.")
                println("XXX: Storing wrapping keys grouped by tenantId into state manager db.")
            }

            stateManager!!.create(records)

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
                            KeyType.UNMANAGED
                        ),
                        timestamp // TODO: probably remove timestamp? not sure if we need this here. If we are keeping the track of start time, than yes, this is useful
                    )
                }.toList()
            )
        }

        return emptyList()
    }

    private fun hasPreviousRotationFinished(oldKeyAlias: String): Boolean {
        stateManager!!.findByMetadataMatchingAll(
            listOf(
                MetadataFilter("rootKeyAlias", Operation.Equals, oldKeyAlias),
                MetadataFilter("type", Operation.Equals, "keyRotation")
            )
        ).forEach {
            // if we find one In Progress status, we know we are not done
            if (it.value.metadata["status"] != "Done") return false
        }
        return true
    }

    private fun deleteStateManagerRecords(oldParentKeyAlias: String) {
        val toDelete = stateManager!!.findByMetadataMatchingAll(
            listOf(
                MetadataFilter("rootKeyAlias", Operation.Equals, oldParentKeyAlias),
                MetadataFilter("type", Operation.Equals, "keyRotation")
            )
        )
        println("XXX: deleting following records: ${toDelete.keys} for rootKeyAlias: $oldParentKeyAlias")
        val failedToDelete = stateManager.delete(
            toDelete.values
        )

        // TODO should we throw the exception?
        if (failedToDelete.isNotEmpty()) println("XXX: RekeyBusProcessor failed to delete following states from the state manager: ${failedToDelete.keys}")    }
}
