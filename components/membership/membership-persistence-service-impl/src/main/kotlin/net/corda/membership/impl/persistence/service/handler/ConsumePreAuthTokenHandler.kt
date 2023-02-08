package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ConsumePreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toCorda

internal class ConsumePreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<ConsumePreAuthToken, Unit>(persistenceHandlerServices) {

    override fun invoke(context: MembershipRequestContext, request: ConsumePreAuthToken) {
        val requestReceived = clock.instant()
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val token = em.find(
                PreAuthTokenEntity::class.java,
                request.tokenId
            ) ?: throw MembershipPersistenceException("Pre-auth token '${request.tokenId}' does not exist.")

            if (MemberX500Name.parse(token.ownerX500Name) != MemberX500Name.Companion.parse(request.ownerX500Name)) {
                throw MembershipPersistenceException("Pre-auth token '${request.tokenId}' does not exist for " +
                        "${request.ownerX500Name}.")
            }

            token.ttl?.run {
                if (this > requestReceived) {
                    throw MembershipPersistenceException("Pre-auth token '${request.tokenId}' expired at $this")
                }
            }
            if (token.status.lowercase() != PreAuthTokenStatus.AVAILABLE.toString().lowercase()) {
                throw MembershipPersistenceException(
                    "Pre-auth token '${request.tokenId}' is not in " +
                            "${PreAuthTokenStatus.AVAILABLE} status. Status is ${token.status}"
                )
            }

            token.status = PreAuthTokenStatus.CONSUMED.toString()
            token.removalRemark = "Token consumed at $requestReceived"
            em.merge(token)
        }
    }
}