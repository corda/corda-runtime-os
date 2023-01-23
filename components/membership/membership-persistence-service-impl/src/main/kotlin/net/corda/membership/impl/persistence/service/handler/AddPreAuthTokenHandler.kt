package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddPreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.virtualnode.toCorda

internal class AddPreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<AddPreAuthToken, Unit>(persistenceHandlerServices) {

    override fun invoke(context: MembershipRequestContext, request: AddPreAuthToken) {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            em.persist(
                PreAuthTokenEntity(
                    request.tokenId,
                    request.ownerX500Name,
                    request.ttl,
                    PreAuthTokenStatus.AVAILABLE.toString(),
                    creationRemark = request.remark,
                    removalRemark = null
                )
            )
        }
    }
}