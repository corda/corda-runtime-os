package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.data.membership.GroupParameters as GroupParametersAvro
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.toMap
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.toCorda
import java.time.Instant

class GroupParametersProcessor(
    private val membershipGroupReadCache: MembershipGroupReadCache,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : CompactedProcessor<String, GroupParametersAvro> {
    override val keyClass: Class<String>
        get() = String::class.java

    override val valueClass: Class<GroupParametersAvro>
        get() = GroupParametersAvro::class.java

    override fun onNext(
        newRecord: Record<String, GroupParametersAvro>,
        oldValue: GroupParametersAvro?,
        currentData: Map<String, GroupParametersAvro>
    ) {
        newRecord.value?.let { newGroupParams ->
            createGroupParameters(newGroupParams.groupParameters).apply {
                membershipGroupReadCache.groupParametersCache.put(
                    newGroupParams.viewOwner.toCorda(),
                    this
                )
            }
        }
    }

    override fun onSnapshot(currentData: Map<String, GroupParametersAvro>) {
        currentData.entries.groupBy(
            { it.value.viewOwner.toCorda() },
            { createGroupParameters(it.value.groupParameters) }
        ).forEach { (owner, groupParams) ->
            membershipGroupReadCache.groupParametersCache.put(owner, groupParams.single())
        }
    }

    private fun createGroupParameters(groupParamsEntries: KeyValuePairList): GroupParameters =
        GroupParametersImpl(layeredPropertyMapFactory.createMap(groupParamsEntries.toMap()))
}

class GroupParametersImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, GroupParameters {
    companion object {
        const val EPOCH_KEY = "corda.epoch"
        const val MPV_KEY = "corda.minimumPlatformVersion"
        const val MODIFIED_TIME_KEY = "corda.modifiedTime"
    }

    init {
        require(minimumPlatformVersion > 0) { "Platform version must be at least 1." }
        require(epoch > 0) { "Epoch must be at least 1." }
    }

    override val minimumPlatformVersion: Int
        get() = map.parse(MPV_KEY, Int::class.java)

    override val modifiedTime: Instant
        get() = map.parse(MODIFIED_TIME_KEY, Instant::class.java)

    override val epoch: Int
        get() = map.parse(EPOCH_KEY, Int::class.java)
}