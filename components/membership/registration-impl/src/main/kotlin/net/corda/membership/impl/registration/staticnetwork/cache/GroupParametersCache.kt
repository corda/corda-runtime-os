package net.corda.membership.impl.registration.staticnetwork.cache

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.staticgroup.StaticGroupDefinition
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.addNewNotaryService
import net.corda.membership.lib.toMap
import net.corda.membership.lib.updateExistingNotaryService
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_STATIC_NETWORK_TOPIC
import net.corda.utilities.time.UTCClock
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class GroupParametersCache(
    private val platformInfoProvider: PlatformInfoProvider,
    private val publisher: Publisher,
    private val keyEncodingService: KeyEncodingService
) {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val clock = UTCClock()

        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()
    }

    private val cache = ConcurrentHashMap<String, KeyValuePairList>()

    /**
     * Sets group parameters for the specified group. Typically used by an event processor to update the cache.
     */
    fun set(groupId: String, groupParameters: KeyValuePairList) {
        cache[groupId] = groupParameters
    }

    /**
     * Retrieves group parameters for the specified holding identity. If group parameters do not exist, this method creates
     * the initial group parameters snapshot for the group and publishes them to Kafka before returning them.
     *
     * @param holdingIdentity Holding identity of the member requesting the group parameters.
     *
     * @return Group parameters for the group if present, or newly created snapshot of group parameters.
     */
    fun getOrCreateGroupParameters(holdingIdentity: HoldingIdentity): KeyValuePairList =
        cache[holdingIdentity.groupId] ?: createGroupParametersSnapshot(holdingIdentity)

    /**
     * Adds a notary to the group parameters. Adds new (or rotated) notary keys if the specified notary service exists,
     * or creates a new notary service.
     *
     * @param notary Notary to be added.
     *
     * @return Updated group parameters with notary information.
     */
    fun addNotary(notary: MemberInfo): KeyValuePairList? {
        val groupId = notary.groupId
        val groupParameters = cache[groupId]?.toMap()
            ?: throw MembershipRegistrationException("Cannot add notary information - no group parameters found.")
        val notaryDetails = notary.notaryDetails
            ?: throw MembershipRegistrationException("Cannot add notary information - '${notary.name}' does not have role set to 'notary'.")
        val notaryServiceName = notaryDetails.serviceName.toString()
        val notaryServiceNumber = groupParameters.entries.firstOrNull { it.value == notaryServiceName }?.run {
            notaryServiceRegex.find(key)?.groups?.get(1)?.value?.toIntOrNull()
        }
        val (_, updated) = if (notaryServiceNumber != null) {
            updateExistingNotaryService(groupParameters, notaryDetails, notaryServiceNumber, keyEncodingService, logger)
        } else {
            addNewNotaryService(groupParameters, notaryDetails, keyEncodingService, logger)
        }

        updated?.publish(groupId)

        return updated
    }

    private fun createGroupParametersSnapshot(holdingIdentity: HoldingIdentity): KeyValuePairList {
        return KeyValuePairList(
            listOf(
                KeyValuePair(EPOCH_KEY, "1"),
                KeyValuePair(MPV_KEY, platformInfoProvider.activePlatformVersion.toString()),
                KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
            )
        ).apply {
            set(holdingIdentity.groupId, this)
            publish(holdingIdentity.groupId)
        }
    }

    private fun KeyValuePairList.publish(groupId: String) {
        publisher.publish(
            listOf(
                Record(
                    MEMBERSHIP_STATIC_NETWORK_TOPIC,
                    groupId,
                    StaticGroupDefinition(groupId, this)
                )
            )
        ).forEach { it.get() }
    }
}