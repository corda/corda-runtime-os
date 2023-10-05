package net.corda.crypto.service.impl.bus


import net.corda.crypto.core.CryptoService
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Crypto.REWRAP_MESSAGE_TOPIC
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * This processor goes through the databases and find out what keys need re-wrapping.
 * It then posts a message to Kafka for each key needing re-wrapping with the tenant ID.
 */
class CryptoRekeyBusProcessor(
    val cryptoService: CryptoService,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val wrappingRepositoryFactory: WrappingRepositoryFactory,
    private val publisherFactory: PublisherFactory,
    private val messagingConfig: SmartConfig,
) : DurableProcessor<String, KeyRotationRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = KeyRotationRequest::class.java
    private val uploadTopic = REWRAP_MESSAGE_TOPIC

    override fun onNext(events: List<Record<String, KeyRotationRequest>>): List<Record<*, *>> {
        events.forEach {
            // we want to compute how many keys we need to rotate
            // root (unmanaged) keys can be used in clusterDB and vNodeDB
            // then for each key we will send a record on Kafka
            // We do not have a code that deals with rewrapping the root key in a cluster DB
            //

            // tenantId in the request is useful ONLY for re-wrapping managed keys!
            // For unmanaged (root) key we need to go through all vNodes and check if the oldKeyAlias is used there
            // if yes, then we need to issue a new record for this key to be re-wrapped
            // we get the tenantId from the vNode info (I hope).
            // Plus we need to query somehow the cluster DB to find out if the root key is used there

            val request = it.value
            if (request == null) {
                logger.error("Unexpected null payload for event with the key={} in topic={}", it.key, it.topic)
                return emptyList() // cannot send any error back as have no idea where to send to
            }

            var tenantIdsWithKeysToRotate = mutableListOf<String>()
            var keysToRotate: Int = 0

            // Get all the virtual nodes, and check if the wrapping repository contains the key that needs to be rotated.
            // We need to calculate the total amount of the keys that need to be rotated regardless the limit being set,
            // as we need to report the status with this total amount.
            val virtualNodeInfo = virtualNodeInfoReadService.getAll()
            virtualNodeInfo.forEach { virtualNode ->
                val tenantId = virtualNode.holdingIdentity.x500Name.commonName.toString()
                val wrappingRepo = wrappingRepositoryFactory.create(tenantId)
                if (wrappingRepo.findKey(request.oldKeyAlias) != null) {
                    keysToRotate++
                    tenantIdsWithKeysToRotate.add(tenantId)
                }
                wrappingRepo.close()
            }

            val publisher = publisherFactory.createPublisher(
                PublisherConfig(request.requestId),
                messagingConfig
            )

            // For each tenant, whose wrapping repo contains key that needs rotating, create a Kafka record and publish it
            // to-do this needs to be updated as there might be millions of records, and we might try to do it more efficiently

            // If the user sets up the limit of the individual key rotations, do only that number of rotations
            // We are rotating root keys at the moment, so for each tenant there will be maximum one key, hence we can
            // limit the number from tenantIds.
            if (request.limit != null) {
                tenantIdsWithKeysToRotate = tenantIdsWithKeysToRotate.take(request.limit).toMutableList()
            }

            tenantIdsWithKeysToRotate.forEach { tenantId ->
                publisher.publish(
                    listOf(
                        Record(
                            uploadTopic,
                            request.requestId,
                            createIndividualKeyRotationRequest(
                                request.requestId,
                                tenantId,
                                request.oldKeyAlias,
                                request.newKeyAlias
                            )
                        )
                    )
                )
            }
            publisher.close()
        }
        return emptyList()
    }

    private fun createKeyRotationStatus(
        request: KeyRotationRequest,
        numberOfKeysToBeRotated: Int,
        alreadyRotatedKeys: Int
    ) = KeyRotationStatus(
        request.requestId,
        request.managedKey,
        request.oldKeyAlias,
        request.newKeyAlias,
        request.oldGeneration,
        request.tenantId,
        request.simulate,
        null,
        alreadyRotatedKeys,
        numberOfKeysToBeRotated,
        Instant.now(),
        Instant.now()
    )

    private fun createIndividualKeyRotationRequest(
        requestId: String,
        tenantId: String,
        oldKeyAlias: String,
        newKeyAlias: String
    ) = IndividualKeyRotationRequest(
        requestId,
        tenantId,
        oldKeyAlias,
        newKeyAlias
    )
}
