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

            var tenantIdsWithKeysToRotate = mutableListOf<String>()
            var keysToRotate: Int = 0

            // Get all the virtual nodes, and check if the wrapping repository contains the key that needs to be rotated.
            val virtualNodeInfo = virtualNodeInfoReadService.getAll()
            virtualNodeInfo.forEach { virtualNode ->
                val wrappingRepo = wrappingRepositoryFactory.create(virtualNode.holdingIdentity.toString())
                if (wrappingRepo.findKey(request!!.oldKeyAlias) != null) {
                    keysToRotate++
                    tenantIdsWithKeysToRotate.add(virtualNode.holdingIdentity.toString())
                    // issue a Kafka record for the key to be re-wrapped

                }
            }

            val publisher = publisherFactory.createPublisher(
                PublisherConfig(request!!.requestId),
                messagingConfig
            )
            publisher.start()


            // For each tenant, whose wrapping repo contains key to be rotated, create a Kafka record and publish it
            // to-do this needs to be updated as there might be millions of records and we might try to do it more efficient
            tenantIdsWithKeysToRotate.forEach { tenant ->
                publisher.publish(
                    listOf(
                        Record(
                            uploadTopic,
                            request.requestId,
                            createIndividualKeyRotationRequest(
                                request.requestId,
                                tenant,
                                request.oldKeyAlias,
                                request.newKeyAlias
                            )
                        )
                    )
                )
            }
        }
        return events
    }

    private fun createResponseContext(
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
