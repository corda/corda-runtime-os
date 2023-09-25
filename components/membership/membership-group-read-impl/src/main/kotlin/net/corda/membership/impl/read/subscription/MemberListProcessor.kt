package net.corda.membership.impl.read.subscription

import net.corda.data.membership.PersistentMemberInfo
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.MemberInfoFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.toCorda

/**
 * Processor for handling updates to the member list topic.
 * On each update the member list cache should be updated so that other services can access the latest data.
 */
class MemberListProcessor(
    private val membershipGroupReadCache: MembershipGroupReadCache,
    private val memberInfoFactory: MemberInfoFactory,
    private val onReady: (membershipGroupReadCache: MembershipGroupReadCache) -> Unit
) : CompactedProcessor<String, PersistentMemberInfo> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<PersistentMemberInfo>
        get() = PersistentMemberInfo::class.java

    /**
     * Populate the member list cache on initial snapshot.
     */
    override fun onSnapshot(currentData: Map<String, PersistentMemberInfo>) {
        currentData.entries.groupBy(
            { it.value.viewOwningMember },
            { memberInfoFactory.createMemberInfo(it.value) }
        ).forEach { (owner, memberInfos) ->
            membershipGroupReadCache.memberListCache.put(owner.toCorda(), memberInfos)
        }
        // signal to lifecycle handling that the on snapshot finished running and the services can start
        onReady(membershipGroupReadCache)
    }

    /**
     * Receive a new record and update the member list cache.
     */
    override fun onNext(
        newRecord: Record<String, PersistentMemberInfo>,
        oldValue: PersistentMemberInfo?,
        currentData: Map<String, PersistentMemberInfo>
    ) {
        newRecord.value?.let { newMemberInfo ->
            memberInfoFactory.createMemberInfo(newMemberInfo).apply {
                membershipGroupReadCache.memberListCache.put(
                    newMemberInfo.viewOwningMember.toCorda(),
                    this
                )
            }
        }
    }
}
