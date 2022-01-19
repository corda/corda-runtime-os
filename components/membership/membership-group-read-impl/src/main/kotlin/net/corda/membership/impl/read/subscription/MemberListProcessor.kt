package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.membership.identity.MGMContextImpl
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.toMemberInfo
import net.corda.membership.identity.toSortedMap
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.membership.conversion.PropertyConverter
import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

/**
 * Processor for handling updates to the member list topic.
 * On each update the member list cache should be updated so that other services can access the latest data.
 */
class MemberListProcessor(
    private val membershipGroupReadCache: MembershipGroupReadCache,
    private val converter: PropertyConverter
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
        val memberInfoView = mutableMapOf<HoldingIdentity, MutableList<MemberInfo>>()
        currentData.forEach { (_, persistentMemberInfo) ->
            toMemberInfo(
                MemberContextImpl(
                    getContextMap(persistentMemberInfo.signedMemberInfo.memberContext),
                    converter
                ),
                MGMContextImpl(
                    getContextMap(persistentMemberInfo.signedMemberInfo.mgmContext),
                    converter
                )
            ).apply {
                val owner = persistentMemberInfo.viewOwningMember
                if (memberInfoView[owner] == null) {
                    memberInfoView[owner] = mutableListOf()
                }
                memberInfoView[owner]!!.add(this)
            }
        }

        memberInfoView.forEach { (owner, memberInfos) ->
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
            MemberContextImpl(
                getContextMap(newRecord.value!!.signedMemberInfo.memberContext),
                converter
            ),
            MGMContextImpl(
                getContextMap(newRecord.value!!.signedMemberInfo.mgmContext),
                converter
            )
        ).apply {
            membershipGroupReadCache.memberListCache.put(
                newRecord.value!!.viewOwningMember.toCorda(),
                this
            )
        }
    }
}
