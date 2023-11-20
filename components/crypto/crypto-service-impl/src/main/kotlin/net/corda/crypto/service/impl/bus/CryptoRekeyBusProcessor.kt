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

            val allTenantIds = virtualNodeTenantIds+listOf(CryptoTenants.CRYPTO, CryptoTenants.P2P, CryptoTenants.REST)
            logger.debug("Found ${allTenantIds.size} tenants; first few are: ${allTenantIds.take(10)}")
            val targetWrappingKeys = allTenantIds.asSequence().map { tenantId ->
                wrappingRepositoryFactory.create(tenantId).use { wrappingRepo ->
                    wrappingRepo.findKeysWrappedByAlias (request.oldKeyAlias).map { wki -> tenantId to wki}
                }
            }.flatten()

            rekeyPublisher.publish(
                targetWrappingKeys.map { (tenantId, wrappingKeyInfo) ->
                    Record(
                        REWRAP_MESSAGE_TOPIC,
                        request.requestId,
                        IndividualKeyRotationRequest(request.requestId,
                            tenantId,
                            request.oldKeyAlias,
                            request.newKeyAlias,
                            wrappingKeyInfo . alias,
                            KeyType.UNMANAGED
                        )
                    )
                }.toList()
            )
        }

        return emptyList()
    }
}
