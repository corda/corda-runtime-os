package net.corda.membership.impl.read.subscription

import net.corda.data.membership.PersistentGroupParameters
import net.corda.data.membership.SignedGroupParameters
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.lib.exceptions.FailedGroupParametersDeserialization
import net.corda.membership.lib.GroupParametersFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GroupParametersProcessor(
    private val groupParametersCache: MemberDataCache<GroupParameters>,
    private val groupParametersFactory: GroupParametersFactory
) : CompactedProcessor<String, PersistentGroupParameters> {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override val keyClass: Class<String>
        get() = String::class.java

    override val valueClass: Class<PersistentGroupParameters>
        get() = PersistentGroupParameters::class.java

    override fun onNext(
        newRecord: Record<String, PersistentGroupParameters>,
        oldValue: PersistentGroupParameters?,
        currentData: Map<String, PersistentGroupParameters>
    ) {
        newRecord.value?.let {
            try {
                groupParametersCache.put(
                    it.viewOwner.toCorda(),
                    createGroupParameters(it.groupParameters)
                )
            } catch (ex: FailedGroupParametersDeserialization) {
                logger.error("Failed to deserialise group parameters. Group parameters cache was not updated.", ex)
            }
        }
    }

    override fun onSnapshot(currentData: Map<String, PersistentGroupParameters>) {
        currentData.entries.forEach {
            try {
                groupParametersCache.put(
                    it.value.viewOwner.toCorda(),
                    createGroupParameters(it.value.groupParameters)
                )
            } catch (ex: FailedGroupParametersDeserialization) {
                logger.error("Failed to deserialise group parameters. Group parameters cache was not updated.", ex)
            }
        }
    }

    private fun createGroupParameters(signedGroupParameters: SignedGroupParameters) =
        groupParametersFactory.create(signedGroupParameters)
}