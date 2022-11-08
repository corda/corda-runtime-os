package net.corda.membership.impl.registration.staticnetwork.cache

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
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import java.util.concurrent.ConcurrentHashMap

class GroupParametersCache(
    private val platformInfoProvider: PlatformInfoProvider,
    private val publisher: Publisher,
    private val keyEncodingService: KeyEncodingService
) {
    private companion object {
        val logger = contextLogger()
        val clock = UTCClock()

        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()
    }

    private val cache = ConcurrentHashMap<String, KeyValuePairList>()


    fun set(groupId: String, groupParameters: KeyValuePairList) {
        cache[groupId] = groupParameters
    }

    fun getOrCreateGroupParameters(holdingIdentity: HoldingIdentity): KeyValuePairList =
        cache[holdingIdentity.groupId] ?: createGroupParametersSnapshot(holdingIdentity)

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
        val groupParameters = KeyValuePairList(
            listOf(
                KeyValuePair(EPOCH_KEY, "1"),
                KeyValuePair(MPV_KEY, platformInfoProvider.activePlatformVersion.toString()),
                KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
            )
        )
        val groupId = holdingIdentity.groupId

        set(groupId, groupParameters)

        groupParameters.publish(groupId)

        return groupParameters
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