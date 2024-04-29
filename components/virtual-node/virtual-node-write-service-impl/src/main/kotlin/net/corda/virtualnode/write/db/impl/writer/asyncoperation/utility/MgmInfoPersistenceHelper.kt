package net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility

import net.corda.data.membership.PersistentMemberInfo
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity

class MgmInfoPersistenceHelper(
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val memberInfoFactory: MemberInfoFactory,
) {
    fun persistMgmMemberInfo(viewOwner: HoldingIdentity, records: List<Record<*, *>>, numOfRetries: Int = 0) {
        val persistentMgmInfo = records.first().value as? PersistentMemberInfo
        if (persistentMgmInfo != null) {
            val mgmInfoPersistenceResult = membershipPersistenceClient.persistMemberInfo(
                viewOwner,
                listOf(memberInfoFactory.createMgmSelfSignedMemberInfo(persistentMgmInfo)),
            ).execute()
            if (mgmInfoPersistenceResult is MembershipPersistenceResult.Failure) {
                // re-try in case the VirtualNodeInfoReadService haven't picked up the records yet
                if (numOfRetries < 5 &&
                    mgmInfoPersistenceResult.errorMsg.contains("Virtual node info can't be retrieved")
                ) {
                    persistMgmMemberInfo(viewOwner, records, numOfRetries + 1)
                }
                throw CordaRuntimeException("Persisting of MGM information failed. ${mgmInfoPersistenceResult.errorMsg}")
            }
        } else {
            throw CordaRuntimeException("Could not find MGM information to persist.")
        }
    }
}
