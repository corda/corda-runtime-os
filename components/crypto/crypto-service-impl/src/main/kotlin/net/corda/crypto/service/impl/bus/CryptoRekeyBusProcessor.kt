package net.corda.crypto.service.impl.bus


import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
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
) : DurableProcessor<String, KeyRotationRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = KeyRotationRequest::class.java

    @Suppress("NestedBlockDepth")
    override fun onNext(events: List<Record<String, KeyRotationRequest>>): List<Record<*, *>> {
        logger.debug("received ${events.size} key rotation requests")
        events.map { it.value}.filterNotNull().forEach { request ->
            logger.debug("processing $request")
            // root (unmanaged) keys can be used in clusterDB and vNodeDB
            // then for each key we will send a record on Kafka
            // We do not have a code that deals with rewrapping the root key in a cluster DB

            // tenantId in the request is useful ONLY for re-wrapping managed keys!
            // For unmanaged (root) key we need to go through all vNodes and check if the oldKeyAlias is used there
            // if yes, then we need to issue a new record for this key to be re-wrapped
            // we get the tenantId from the vNode info (I hope).

            val virtualNodeInfo = virtualNodeInfoReadService.getAll() // Get all the virtual nodes
            val virtualNodeTenantIds = virtualNodeInfo.map { it.holdingIdentity.shortHash.toString() }

            val allTenantIds = virtualNodeTenantIds+listOf(CryptoTenants.CRYPTO, CryptoTenants.P2P, CryptoTenants.REST)
            logger.debug("Found ${allTenantIds.size} tenants; first few are: ${allTenantIds.take(10)}")
            val targetWrappingKeys = allTenantIds.asSequence().map { tenantId ->
                wrappingRepositoryFactory.create(tenantId).use { wrappingRepo ->
                    wrappingRepo.findKeysWrappedByAlias (request.oldKeyAlias).map { wki -> tenantId to wki}
                }
            }.flatten()


            targetWrappingKeys.map { (tenantId, wrappingKeyInfo) ->
                val newGeneration = cryptoService.rewrapWrappingKey(tenantId, wrappingKeyInfo.alias, request.newKeyAlias)
                logger.debug("Rewrapped ${wrappingKeyInfo.alias} in tenant ${tenantId} from "+
                        "${wrappingKeyInfo.parentKeyAlias} to ${request.newKeyAlias}; "+
                        "generation number now ${newGeneration}")
            }.count()
        }
        return emptyList()
    }
}
