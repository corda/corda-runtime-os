package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ConsumePreAuthToken
import net.corda.membership.db.lib.ConsumePreAuthTokenService
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toCorda

internal class ConsumePreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<ConsumePreAuthToken, Unit>(persistenceHandlerServices) {

    private val consumer = ConsumePreAuthTokenService(clock)

    override fun invoke(context: MembershipRequestContext, request: ConsumePreAuthToken) {
        logger.info("Consuming pre-auth token with ID ${request.tokenId}")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            consumer.consume(
                em,
                MemberX500Name.parse(request.ownerX500Name),
                request.tokenId,
            )
        }
    }
}
