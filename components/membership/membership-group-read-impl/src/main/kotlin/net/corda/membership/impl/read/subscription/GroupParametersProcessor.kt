package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.data.membership.GroupParameters as GroupParametersAvro
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.lib.impl.GroupParametersImpl
import net.corda.membership.lib.toMap
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.toCorda

class GroupParametersProcessor(
    private val groupParametersCache: MemberDataCache<GroupParameters>,
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
        newRecord.value?.let {
            groupParametersCache.put(
                it.viewOwner.toCorda(),
                createGroupParameters(it.groupParameters)
            )
        }
    }

    override fun onSnapshot(currentData: Map<String, GroupParametersAvro>) {
        currentData.entries.forEach {
            groupParametersCache.put(
                it.value.viewOwner.toCorda(),
                createGroupParameters(it.value.groupParameters)
            )
        }
    }

    private fun createGroupParameters(groupParamsEntries: KeyValuePairList): GroupParameters =
        GroupParametersImpl(layeredPropertyMapFactory.createMap(groupParamsEntries.toMap()))
}