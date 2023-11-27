package net.corda.crypto.service.impl.bus


import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
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
class CryptoRekeyBusProcessor(
    val cryptoService: CryptoService,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val wrappingRepositoryFactory: WrappingRepositoryFactory,
    private val rekeyPublisher: Publisher
) : DurableProcessor<String, KeyRotationRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = KeyRotationRequest::class.java
    @Suppress("NestedBlockDepth")
    override fun onNext(events: List<Record<String, KeyRotationRequest>>): List<Record<*, *>> {
        logger.debug("received ${events.size} key rotation requests")
        events.mapNotNull { it.value }.forEach { request ->
            logger.debug("processing $request")
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
            // since they share the cluster crypto database. So we scan over the virtual node tenants and an arbitary
            // choice of cluster level tenant. We pick CryptoTenants.CRYPTO as the arbitrary cluster level tenant,
            // and we should not also check CryptoTenants.P2P and CryptoTenants.REST since if we do we'll get duplicate.
            val allTenantIds = virtualNodeTenantIds+listOf(CryptoTenants.CRYPTO)
            logger.debug("Found ${allTenantIds.size} tenants; first few are: ${allTenantIds.take(10)}")
            val targetWrappingKeys = allTenantIds.asSequence().map { tenantId ->
                wrappingRepositoryFactory.create(tenantId).use { wrappingRepo ->
                    wrappingRepo.findKeysWrappedByAlias (request.oldParentKeyAlias).map { wki -> tenantId to wki}
                }
            }.flatten()
            rekeyPublisher.publish(
                targetWrappingKeys.map { (tenantId, wrappingKeyInfo) ->
                    Record(
                        REWRAP_MESSAGE_TOPIC,
                        UUID.randomUUID().toString(),
                        IndividualKeyRotationRequest(request.requestId,
                            tenantId,
                            request.oldParentKeyAlias,
                            request.newParentKeyAlias,
                            wrappingKeyInfo.alias,
                            KeyType.UNMANAGED
                        )
                    )
                }.toList()
            )
        }

        return emptyList()
    }
}
