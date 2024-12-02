package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddPreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.lib.exceptions.ConflictPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.EntityExistsException

internal class AddPreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<AddPreAuthToken, Unit>(persistenceHandlerServices) {
    override val operation = AddPreAuthToken::class.java
    override fun invoke(context: MembershipRequestContext, request: AddPreAuthToken) {
        return try {
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
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
        } catch (_: EntityExistsException) {
            throw ConflictPersistenceException("Token with ID: '${request.tokenId}' already exists.")
        }
    }
}
