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
    fun persistMgmMemberInfo(viewOwner: HoldingIdentity, records: List<Record<*, *>>) {
        val persistentMgmInfo = records.first().value as? PersistentMemberInfo
        if (persistentMgmInfo != null) {
            val mgmInfoPersistenceResult = membershipPersistenceClient.persistMemberInfo(
                viewOwner,
                listOf(memberInfoFactory.createMgmSelfSignedMemberInfo(persistentMgmInfo)),
            ).execute()
            if (mgmInfoPersistenceResult is MembershipPersistenceResult.Failure) {
                throw CordaRuntimeException("Persisting of MGM information failed.")
            }
        } else {
            throw CordaRuntimeException("Could not find MGM information to persist.")
        }
    }
}
