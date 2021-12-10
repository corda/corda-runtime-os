package net.corda.membership.impl.read.processor

import net.corda.data.membership.SignedMemberInfo
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record

/**
 * Processor for handling updates to the member list topic.
 * On each update the member list cache should be updated so that other services can access the latest data.
 */
class MemberListProcessor(
    private val memberListCache: MemberListCache
): CompactedProcessor<String, SignedMemberInfo> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<SignedMemberInfo>
        get() = SignedMemberInfo::class.java

    /**
     * Populate the member list cache on initial snapshot.
     */
    override fun onSnapshot(currentData: Map<String, SignedMemberInfo>) {
        TODO("Not yet implemented")
    }

    /**
     * Receive a new record and update the member list cache.
     */
    override fun onNext(newRecord: Record<String, SignedMemberInfo>, oldValue: SignedMemberInfo?, currentData: Map<String, SignedMemberInfo>) {
        TODO("Not yet implemented")
    }
}