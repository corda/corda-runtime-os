package net.corda.membership.service.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.service.impl.actions.DistributeMemberInfoActionHandler
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class MembershipActionsProcessor(
    membershipQueryClient: MembershipQueryClient,
    cipherSchemeMetadata: CipherSchemeMetadata,
    clock: Clock,
    cryptoOpsClient: CryptoOpsClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    merkleTreeProvider: MerkleTreeProvider,
    membershipConfig: SmartConfig,
    groupReaderProvider: MembershipGroupReaderProvider,
    locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
): DurableProcessor<String, MembershipActionsRequest> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val distributeMemberInfoActionHandler = DistributeMemberInfoActionHandler(
        membershipQueryClient,
        cipherSchemeMetadata,
        clock,
        cryptoOpsClient,
        cordaAvroSerializationFactory,
        merkleTreeProvider,
        membershipConfig,
        groupReaderProvider,
        locallyHostedIdentitiesService,
    )

    override fun onNext(events: List<Record<String, MembershipActionsRequest>>): List<Record<String, *>> {
        return events.flatMap { event -> processEvent(event) }.toList()
    }

    private fun processEvent(event: Record<String, MembershipActionsRequest>): List<Record<String, *>> {
        event.value?.request?.let { request ->
            return when (request) {
                is DistributeMemberInfo -> distributeMemberInfoActionHandler.process(event.key, request)
                else -> {
                    logger.error("Received unimplemented membership action request.")
                    emptyList()
                }
            }
        }
        return emptyList()
    }

    override val keyClass = String::class.java
    override val valueClass = MembershipActionsRequest::class.java
}