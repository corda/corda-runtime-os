package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePairList
import net.corda.data.membership.SignedMemberInfo
import net.corda.membership.identity.MGMContextImpl
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.MemberInfoExtension.Companion.groupId
import net.corda.membership.identity.toMemberInfo
import net.corda.membership.identity.toSortedMap
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.membership.conversion.PropertyConverter
import net.corda.virtualnode.HoldingIdentity

/**
 * Processor for handling updates to the member list topic.
 * On each update the member list cache should be updated so that other services can access the latest data.
 */
class MemberListProcessor(
    private val membershipGroupReadCache: MembershipGroupReadCache,
    private val converter: PropertyConverter
) : CompactedProcessor<String, SignedMemberInfo> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<SignedMemberInfo>
        get() = SignedMemberInfo::class.java

    /**
     * Populate the member list cache on initial snapshot.
     */
    override fun onSnapshot(currentData: Map<String, SignedMemberInfo>) {
        currentData.forEach { (_, signedMemberInfo) ->
            toMemberInfo(
                MemberContextImpl(
                    KeyValuePairList.fromByteBuffer(signedMemberInfo.memberContext).toSortedMap(),
                    converter
                ),
                MGMContextImpl(
                    KeyValuePairList.fromByteBuffer(signedMemberInfo.mgmContext).toSortedMap(),
                    converter
                )
            ).apply {
                membershipGroupReadCache.memberListCache.put(
                    HoldingIdentity(this.name.toString(), this.groupId),
                    this
                )
            }
        }
    }

    /**
     * Receive a new record and update the member list cache.
     */
    override fun onNext(
        newRecord: Record<String, SignedMemberInfo>,
        oldValue: SignedMemberInfo?,
        currentData: Map<String, SignedMemberInfo>
    ) {
        toMemberInfo(
            MemberContextImpl(
                KeyValuePairList.fromByteBuffer(newRecord.value!!.memberContext).toSortedMap(),
                converter
            ),
            MGMContextImpl(
                KeyValuePairList.fromByteBuffer(newRecord.value!!.mgmContext).toSortedMap(),
                converter
            )
        ).apply {
            membershipGroupReadCache.memberListCache.put(
                HoldingIdentity(this.name.toString(), this.groupId),
                this
            )
        }
    }
}
