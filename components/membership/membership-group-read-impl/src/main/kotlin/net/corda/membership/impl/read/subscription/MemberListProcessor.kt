package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.toMemberInfo
import net.corda.membership.impl.toSortedMap
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

/**
 * Processor for handling updates to the member list topic.
 * On each update the member list cache should be updated so that other services can access the latest data.
 */
class MemberListProcessor(
    private val membershipGroupReadCache: MembershipGroupReadCache,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory
) : CompactedProcessor<String, PersistentMemberInfo> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<PersistentMemberInfo>
        get() = PersistentMemberInfo::class.java

    private fun getContextMap(bytes: ByteBuffer) = KeyValuePairList.fromByteBuffer(bytes).toSortedMap()

    /**
     * Populate the member list cache on initial snapshot.
     */
    override fun onSnapshot(currentData: Map<String, PersistentMemberInfo>) {
        currentData.entries.groupBy(
            { it.value.viewOwningMember },
            {
                toMemberInfo(
                    layeredPropertyMapFactory.create<MemberContextImpl>(
                        getContextMap(it.value.signedMemberInfo.memberContext)
                    ),
                    layeredPropertyMapFactory.create<MGMContextImpl>(
                        getContextMap(it.value.signedMemberInfo.mgmContext)
                    )
                )
            }
        ).forEach { (owner, memberInfos) ->
            membershipGroupReadCache.memberListCache.put(owner.toCorda(), memberInfos)
        }
    }

    /**
     * Receive a new record and update the member list cache.
     */
    override fun onNext(
        newRecord: Record<String, PersistentMemberInfo>,
        oldValue: PersistentMemberInfo?,
        currentData: Map<String, PersistentMemberInfo>
    ) {
        toMemberInfo(
            layeredPropertyMapFactory.create<MemberContextImpl>(
                getContextMap(newRecord.value!!.signedMemberInfo.memberContext)
            ),
            layeredPropertyMapFactory.create<MGMContextImpl>(
                getContextMap(newRecord.value!!.signedMemberInfo.mgmContext)
            )
        ).apply {
            membershipGroupReadCache.memberListCache.put(
                newRecord.value!!.viewOwningMember.toCorda(),
                this
            )
        }
    }
}
