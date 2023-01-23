package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.db.response.command.RevokePreAuthTokenResponse
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.virtualnode.toCorda

internal class RevokePreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices)
    : BasePersistenceHandler<RevokePreAuthToken, RevokePreAuthTokenResponse>(persistenceHandlerServices) {

    companion object {
        fun PreAuthTokenEntity.toAvro(): PreAuthToken {
            return PreAuthToken(
                this.tokenId,
                this.ownerX500Name,
                this.ttl,
                PreAuthTokenStatus.valueOf(this.status),
                this.creationRemark,
                this.removalRemark
            )
        }
    }

    override fun invoke(context: MembershipRequestContext, request: RevokePreAuthToken): RevokePreAuthTokenResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val token = em.find(PreAuthTokenEntity::class.java, request.tokenId)
            token.status = PreAuthTokenStatus.REVOKED.toString()
            token.removalRemark = request.remark
            em.merge(token)
            RevokePreAuthTokenResponse(token.toAvro())
        }
    }
}