package net.corda.membership.service.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.actions.request.DistributeGroupParameters
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.metrics.TimerMetricTypes
import net.corda.membership.lib.metrics.getTimerMetric
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.service.impl.actions.DistributeGroupParametersActionHandler
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
) : DurableProcessor<String, MembershipActionsRequest> {
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

    private val distributeGroupParametersActionHandler = DistributeGroupParametersActionHandler(
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
            return recordTimerMetric(request) {
                when (it) {
                    is DistributeMemberInfo -> distributeMemberInfoActionHandler.process(event.key, it)
                    is DistributeGroupParameters -> distributeGroupParametersActionHandler.process(event.key, it)
                    else -> {
                        logger.error("Received unimplemented membership action request.")
                        emptyList()
                    }
                }
            }
        }
        return emptyList()
    }

    /**
     * Required for each action type for allowing metrics to be tagged by virtual node ID.
     */
    private fun getOwnerHoldingId(request: Any): HoldingIdentity? {
        return when (request) {
            is DistributeMemberInfo -> request.mgm
            else -> null
        }
    }

    private fun recordTimerMetric(
        request: Any,
        func: (request: Any) -> List<Record<String, *>>
    ): List<Record<String, *>> {
        return getTimerMetric(
            TimerMetricTypes.ACTIONS,
            getOwnerHoldingId(request),
            request::class.java.simpleName
        ).recordCallable { func(request) }!!
    }

    override val keyClass = String::class.java
    override val valueClass = MembershipActionsRequest::class.java
}
